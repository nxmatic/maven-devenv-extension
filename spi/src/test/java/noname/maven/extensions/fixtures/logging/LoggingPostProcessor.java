package noname.maven.extensions.fixtures.logging;

import java.util.logging.LogManager;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import noname.maven.extensions.fixtures.plexus.PlexusComponentsInjector;

public class LoggingPostProcessor implements TestInstancePostProcessor {

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {

        // install JUL bridge handler
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();

        // log for having a bootstrapping mark message
        LoggerFactory.getLogger(PlexusComponentsInjector.class).info("bootstrapping");
    }

}