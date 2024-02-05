package noname.maven.devenv.annotations.processor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

import io.vavr.control.Try;
import noname.maven.devenv.internal.plexus.ForeignExtensionPackagesMarshaller;
import noname.maven.devenv.spi.plexus.ForeignExtensionPackages;

@SupportedAnnotationTypes(ForeignExtensionPackages.ANNOTATION_PATH)
@SupportedSourceVersion(SourceVersion.RELEASE_11)
// @SupportedOptions(ForeignExtensionPackagesProcessor.OUTPUT_DIRECTORY_OPTION)
public class ForeignExtensionPackagesProcessor extends AbstractProcessor {

    // static final String OUTPUT_DIRECTORY_OPTION = "output-directory";ss
    // static final String OUTPUT_DIRECTORY_LOCATION = "META-INF/devenv";

    final ForeignExtensionPackages.Marshaller marshaller = new ForeignExtensionPackagesMarshaller();

    final Collector collector = new Collector(new HashMap<>());

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return Try.ofSupplier(() -> new Context(this, annotations, roundEnv))
                  .map(collector::collect)
                  .andThen(collector -> { // write to file only at the end of the rounds
                    if (!roundEnv.processingOver()) {
                        return;
                    }
                    dumpToFile(collector);
                  })
                  .onFailure(e -> processingEnv.getMessager()
                                               .printMessage(Diagnostic.Kind.ERROR,
                                                       "Failed to write YAML file: " + e.getMessage()))
                  .isSuccess();
    }

    void dumpToFile(Collector collector) {
        Try.withResources(
              () -> processingEnv.getFiler()
                                 .createResource(StandardLocation.CLASS_OUTPUT, "",
                                         "META-INF/devenv/" + ForeignExtensionPackages.class.getSimpleName()
                                                 + ".yaml")
                                 .openOutputStream())
                               .of(outputStream -> marshaller.toYaml(outputStream,
                                       collector.getImportedPackages()));
    }

    record Collector(Map<ForeignExtensionPackages.Provider, ForeignExtensionPackages.Builder> builders) {

        Collector collect(Context context) {
            context.annotationTypes.stream()
                                   .flatMap(annotationType -> annotatedTypesOf(context, annotationType))
                                   .peek(annotedType -> printDiagnostic(context, annotedType))
                                   .forEach(this::collect);
            return this;
        }


        Collector collect(AnnotatedType annotatedType) {
            ForeignExtensionPackages.Import annotation = annotatedType.annotatedType()
                                                                      .getAnnotation(
                                                                              ForeignExtensionPackages.Import.class);
            Objects.requireNonNull(annotation, "should have an annotation at that place");
            ForeignExtensionPackages.Provider provider = new ForeignExtensionPackages.Provider(annotation.groupId(),
                    annotation.artifactId());
            builders.computeIfAbsent(provider, key -> new ForeignExtensionPackages.Builder().with(provider))
                    .addPackages(annotation.packages());
            return this;
        }

        
        Stream<AnnotatedType> annotatedTypesOf(Context context, TypeElement annotationType) {
            return context.roundEnvironment.getElementsAnnotatedWith(annotationType)
                                           .stream()
                                           .map(annotatedType -> new AnnotatedType(annotationType, annotatedType));
        }
        
        Set<ForeignExtensionPackages> getImportedPackages() {
            return builders.values().stream().map(ForeignExtensionPackages.Builder::build).collect(Collectors.toSet());

        }
        
        void printDiagnostic(Context context, AnnotatedType annotatedType) {
            context.processor.processingEnv.getMessager()
            .printMessage(Diagnostic.Kind.NOTE,
                    "processing foreign import packages annotations",
                    annotatedType.annotatedType());
        }

        record AnnotatedType(TypeElement annotationType, Element annotatedType) {
        }

    }

    record Context(ForeignExtensionPackagesProcessor processor, Set<? extends TypeElement> annotationTypes,
            RoundEnvironment roundEnvironment) {

    }

}