package noname.maven.devenv.internal.plexus;

// $, Case, Match
import static io.vavr.API.$;
import static io.vavr.API.Case;
// instanceOf
import static io.vavr.Predicates.instanceOf;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.extension.internal.CoreExports;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.eclipse.sisu.EagerSingleton;

import io.vavr.control.Try;
import noname.maven.devenv.spi.DevenvComponent;
import noname.maven.devenv.spi.plexus.ForeignExtensionPackages;
import noname.maven.devenv.spi.plexus.ForeignExtensionPackages.Marshaller;

/**
 * This class is responsible for importing foreign packages required by
 * extensions. It is a singleton and is named with
 * the {@link DevenvComponent#HINT}.
 * <p>
 * Upon initialization, it iterates over all the realms in the
 * {@link ClassWorld}, and for each realm, it creates a
 * {@link ForeignExtensionPackagesImporter} and imports foreign packages. If the
 * import is successful, it logs a message
 * and discovers components in the realm. If it fails, it logs an error.
 * </p>
 * <p>
 * The {@link ForeignExtensionPackagesImporter} record is responsible for
 * importing foreign packages into a
 * {@link ClassRealm}. It takes a {@link ClassRealm} and a map of exported
 * packages as parameters. The
 * {@code importForeignPackages} method finds resources in the
 * {@code foreignPackagesPath}, and for each resource, it
 * creates a {@link ClassRealmForeignPackages} and applies it. The {@code apply}
 * method imports each package into the
 * {@link ClassRealm}.
 * </p>
 * <p>
 * The {@link ClassRealmForeignPackages} record represents a {@link ClassRealm}
 * and its associated packages. It provides
 * a {@code stream} method to stream the packages and a static {@code of} method
 * to create a
 * {@link ClassRealmForeignPackages} from an {@link InputStream}.
 * </p>
 * <p>
 * Considerations:
 * </p>
 * <ul>
 * <li>Error Handling: You're logging errors, which is good, but you might want
 * to consider whether there's any
 * additional error handling you should do. For example, if importing foreign
 * packages fails for a realm, should you
 * continue with the next realm, or should you stop the entire process?</li>
 * <li>Resource Management: In the {@code importForeignPackages} method, you're
 * opening a stream for each URL, but it's
 * not clear if these streams are being closed properly. You might want to use a
 * try-with-resources statement to ensure
 * that these streams are closed.</li>
 * </ul>
 */
@Named(DevenvComponent.HINT)
@EagerSingleton
public class ForeignExtensionPackagesImporter {

    static final Path foreignPackagesPath = Paths.get("META-INF")
            .resolve(DevenvComponent.HINT)
            .resolve(ForeignExtensionPackages.class.getSimpleName()
                    .concat(".yaml"));

    @Inject
    Logger logger;

    @Inject
    ClassWorldScanner classWorldScanner;

    @Inject
    Marshaller marshaller;

    @Inject
    @PostConstruct
    public void initialize() throws InitializationException {
        classWorldScanner.accept(this::importForeignPackages)
                .map(this::mapInitializationError)
                .reduce(Try.success(null), (try1, try2) -> {
                    if (try1.isFailure()) {
                        if (try2.isFailure()) {
                            try1.getCause().addSuppressed(try2.getCause());
                        }
                        return try1;
                    }
                    return try2;
                })
                .get();
    }

    @SuppressWarnings("unchecked")
    Try<Void> mapInitializationError(Try<Void> tryValue) {
        return tryValue.mapFailure(Case($(instanceOf(Importer.ImportException.class)), this::importError),
                Case($(), this::unexpectedError));
    }

    InitializationException importError(Importer.ImportException e) {
        return new InitializationException("Failed to import foreign packages for " + e.classRealm, e);
    }

    InitializationException unexpectedError(Throwable cause) {
        return new InitializationException("Failed to import foreign packages", cause);
    }

    Try<Void> importForeignPackages(ClassWorldScanner.Entry entry) {
        CoreExports coreExports = entry.coreExports();
        ClassRealm classRealm = entry.classRealm();
        PlexusContainer container = entry.container();

        return Try.of(() -> new Importer(logger, classRealm, coreExports,
                marshaller).stream().map(tryImport -> tryImport.andThenTry(importedPackages -> {
                    logger.info(String.format("Rediscovering components for %s: %s", classRealm, importedPackages));
                    container.discoverComponents(classRealm);
                })).reduce(Try.success(null), (try1, try2) -> {
                    if (try1.isFailure()) {
                        if (try2.isFailure()) {
                            try1.getCause().addSuppressed(try2.getCause());
                        }
                        return try1;
                    }
                    return try2;
                }).map(ignored -> (Void) null).get());
    }

    record Importer(Logger logger, ClassRealm classRealm, Map<Pattern, ClassLoader> exportedPackages,
            Marshaller foreignPackagesMarshaller) {

        Importer(Logger logger, ClassRealm classRealm, CoreExports exports,
                Marshaller foreignPackagesMarshaller) {
            this(logger, classRealm, transformExportedPackages(exports.getExportedPackages()),
                    foreignPackagesMarshaller);
        }

        Stream<Try<ForeignExtensionPackages>> stream() throws ImportException {
            return Try.of(() -> Collections.list(classRealm.findResources(foreignPackagesPath.toString())))
                    .mapTry(urls -> urls.stream()
                            .peek(url -> logger.debug("Loading " + url.toExternalForm()))
                            .map(url -> Try.withResources(url::openStream)
                                    .of(stream -> {
                                        logger.debug("Stream opened, unmarshaling...");
                                        return foreignPackagesMarshaller.fromYaml(stream);
                                    }))
                            .flatMap(attempt -> {
                                attempt.onFailure(cause -> logger.error("Failed to unmarshal", cause));
                                return attempt
                                    .map(
                                            packages -> packages.stream().map(this::importForeignPackages))
                                    .getOrElse(Stream.empty());
                                }))
                    .getOrElseThrow(cause -> new ImportException(classRealm, cause));
        }

        Try<ForeignExtensionPackages> importForeignPackages(ForeignExtensionPackages foreignPackages) {
            return Try.success(foreignPackages)
                    .andThen(
                            __ -> foreignPackages.packages()
                                    .forEach(
                                            pkg -> exportedPackages.entrySet()
                                                    .stream()
                                                    .peek(entry -> logger.debug(
                                                            MessageFormat.format(
                                                                    "matching {0} against {1}",
                                                                    pkg, entry.getKey())))
                                                    .anyMatch(entry -> {
                                                        if (entry.getKey()
                                                                .matcher(pkg)
                                                                .matches()) {
                                                            ClassLoader loader = entry.getValue();
                                                            logger.debug(
                                                                    MessageFormat.format(
                                                                            "importing {0} into {1} from {2}",
                                                                            pkg,
                                                                            classRealm,
                                                                            loader));
                                                            classRealm.importFrom(loader,
                                                                    pkg);
                                                            return true;
                                                        }
                                                        return false;
                                                    })));
        }

        static Map<Pattern, ClassLoader> transformExportedPackages(Map<String, ClassLoader> exportedPackages) {
            return exportedPackages.entrySet().stream().map(entry -> {
                String pattern = entry.getKey().replace(".", "\\.").replace("*", ".*");
                return new AbstractMap.SimpleEntry<>(Pattern.compile(pattern), entry.getValue());
            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        class ImportException extends Exception {
            private static final long serialVersionUID = 1L;

            final ClassRealm classRealm;

            ImportException(ClassRealm classRealm, Throwable cause) {
                super("Failed to import foreign packages for " + classRealm, cause);
                this.classRealm = classRealm;
            }
        }

    }
}
