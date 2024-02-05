package noname.maven.devenv.internal;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.Mojo;

import noname.maven.devenv.internal.plexus.ForeignExtensionPackagesImporter;
import noname.maven.devenv.spi.DevenvComponent;

@Mojo(name = DummyMojo.GOAL, instantiationStrategy = InstantiationStrategy.SINGLETON, threadSafe = true)
public class DummyMojo extends AbstractMojo {

    public static final String GOAL = "dummy";

    public static final String FOREIGN_PACKAGES_IMPORTER_NAME = ForeignExtensionPackagesImporter.class.getName();
    
    @Inject
    @Named(DevenvComponent.HINT)
    ForeignExtensionPackagesImporter packagesImporter;
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        ;  
    }

}
