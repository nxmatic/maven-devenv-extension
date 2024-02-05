package noname.maven.extensions.fixtures.plexus;

import javax.inject.Inject;

import org.apache.maven.Maven;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import noname.maven.extensions.fixtures.logging.LoggingPostProcessor;

@ExtendWith( LoggingPostProcessor.class )
@ExtendWith( PlexusComponentsInjector.class )
public class TestPlexusComponentInjection {
    

    @Inject
    PlexusContainer container;
    
    @Inject
    ClassWorld classWorld;
    
    @Inject
    Maven maven;
    
    @Test
    public void isInjected() {
        
        Assert.assertNotNull("container has a right value", container);
        Assert.assertNotNull("classWorld has a right value", classWorld);
        Assert.assertNotNull("maven has a right value", maven);

    }

}
