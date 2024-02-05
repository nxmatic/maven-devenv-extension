package noname.maven.extensions.fixtures.plexus;

import javax.inject.Inject;

import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vavr.control.Try;
import noname.maven.devenv.internal.plexus.ForeignExtensionPackagesImporter;

@ExtendWith(PlexusComponentsInjector.class)
class TestDependenciesInjection {
    
    @Inject
    ForeignExtensionPackagesImporter packagesImporter;
    
    @Inject
    PlexusContainer container;
    
    @Inject
    ClassWorld classWorld;
    

    @Test
    void importerIsInjected() {
        Assert.assertNotNull("importer has a right value", packagesImporter);
    }
    
    @Test
    void pfouh() throws ComponentLookupException {
        ClassRealm realm = container.createChildRealm("fixture");
        Try.success(container).failed();
    }

}
