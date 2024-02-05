package noname.maven.extensions.fixtures.plexus;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import org.apache.maven.artifact.UnknownRepositoryLayoutException;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.settings.MavenSettingsBuilder;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilder;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.sisu.inject.InjectorBindings;
import org.eclipse.sisu.inject.MutableBeanLocator;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import com.google.inject.Binder;
import com.google.inject.Module;

import io.vavr.control.Try;

public class PlexusComponentsInjector implements TestInstancePostProcessor {

    @Override
    public void postProcessTestInstance(final Object testCase, final ExtensionContext context) throws Exception {
        Class<?> testClazz = testCase.getClass();
        String role = testClazz.getName();

        System.setProperty("sisu.debug", "true");

        new Builder().setClassPathScanning(PlexusConstants.SCANNING_ON)
                     .setComponentVisibility(PlexusConstants.GLOBAL_VISIBILITY)
                     .setName(role)
                     .setAutoWiring(true)
                     .inContainer()
                     .initialize()
                     .injectLocalRepository()
                     .inject(context, testCase, role);
    }

    class Builder {
        final DefaultContainerConfiguration config = new DefaultContainerConfiguration();

        Builder setClassPathScanning(String thatValue) {
            config.setClassPathScanning(thatValue);
            return this;
        }

        Builder setComponentVisibility(String thatValue) {
            config.setComponentVisibility(thatValue);
            return this;
        }

        Builder setName(String thatValue) {
            config.setName(thatValue);
            return this;
        }

        Builder setAutoWiring(boolean thatValue) {
            config.setAutoWiring(thatValue);
            return this;
        }

        Publisher inContainer()
                throws PlexusContainerException, ComponentLookupException, UnknownRepositoryLayoutException {
            return new Publisher(config);
        }

        class Publisher {
            DefaultPlexusContainer container;

            Publisher(DefaultContainerConfiguration thatConfig)
                    throws PlexusContainerException, ComponentLookupException, UnknownRepositoryLayoutException {
                container = new DefaultPlexusContainer(thatConfig);
            }

            Publisher initialize() throws UnknownRepositoryLayoutException, ComponentLookupException, MalformedURLException {
                container.discoverComponents(container.getContainerRealm());
                return this;

            }

            Publisher injectLocalRepository() throws UnknownRepositoryLayoutException, MalformedURLException,
                    ComponentLookupException, SettingsBuildingException, IOException, XmlPullParserException {
                container.lookup(ArtifactRepositoryFactory.class)
                         .createArtifactRepository(ArtifactRepositoryFactory.LOCAL_REPOSITORY_ID,
                                 localRepositoryLocation(), ArtifactRepositoryFactory.DEFAULT_LAYOUT_ID, null, null);
                return this;
            }
            
            String localRepositoryLocation() throws MalformedURLException, SettingsBuildingException,
                    ComponentLookupException, IOException, XmlPullParserException {
                Settings settings = settings();
                String localRepository = settings.getLocalRepository();
                return Try.success(localRepository)
                          .filter(Objects::nonNull)
                          .map(Paths::get)
                          .map(Path::toUri)
                          .mapTry(URI::toURL)
                          .map(URL::toString)
                          .getOrElse(() -> System.getProperty("user.home").concat("/.m2/repository"));
            }
            
            Settings settings()
                    throws SettingsBuildingException, ComponentLookupException, IOException, XmlPullParserException {
                return container.lookup(MavenSettingsBuilder.class).buildSettings();
            }

            void inject(ExtensionContext context, Object testCase, String role) throws ComponentLookupException {
                class TestCaseModule implements Module {
                    public void configure(final Binder binder) {
                        binder.requestInjection(testCase);
                    }
                }
                InjectorBindings publisher = new InjectorBindings(
                        container.addPlexusInjector(Collections.emptyList(), new TestCaseModule()), null /* unused */ );
                context.getStore(Namespace.GLOBAL).put(testCase, new ExtensionContext.Store.CloseableResource() {

                    @Override
                    public void close() throws Throwable {
                        container.lookup(MutableBeanLocator.class).remove(publisher);
                    }
                });
            }

        }

    }

}