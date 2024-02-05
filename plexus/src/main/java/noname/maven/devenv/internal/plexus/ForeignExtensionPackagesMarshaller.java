package noname.maven.devenv.internal.plexus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.codehaus.plexus.logging.Logger;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import noname.maven.devenv.spi.DevenvComponent;
import noname.maven.devenv.spi.plexus.ForeignExtensionPackages;
import noname.maven.devenv.spi.plexus.ForeignExtensionPackages.Marshaller;

@Named(DevenvComponent.HINT)
public class ForeignExtensionPackagesMarshaller implements ForeignExtensionPackages.Marshaller {
    
    @Inject
    Logger logger;
    
    final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @Override
    public Set<ForeignExtensionPackages> fromYaml(InputStream input) throws IOException {
        logger.debug("Unmarshalling");
        Set<ForeignExtensionPackages> packages = mapper.readValue(input, new TypeReference<Set<ForeignExtensionPackages>>() {});
        logger.debug("Unmarshalled " + packages.toString());
        return packages;
    }

    @Override
    public Set<ForeignExtensionPackages> toYaml(OutputStream output, Set<ForeignExtensionPackages> packages) throws StreamWriteException, DatabindException, IOException {
        mapper.writeValue(output, packages);
        return packages;
    }

}
