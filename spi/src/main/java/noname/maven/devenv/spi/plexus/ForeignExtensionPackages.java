package noname.maven.devenv.spi.plexus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;

public record ForeignExtensionPackages(Provider provider, Set<String> packages) {
    
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Import {
        String groupId();

        String artifactId();

        String[] packages();
    }
 
    public interface Marshaller {

        Set<ForeignExtensionPackages> fromYaml(InputStream input) throws IOException;

        Set<ForeignExtensionPackages> toYaml(OutputStream output, Set<ForeignExtensionPackages> packages)
                throws StreamWriteException, DatabindException, IOException;

    }
    
    public ForeignExtensionPackages {
        if (packages == null) {
            throw new IllegalArgumentException("packages must not be null or empty");
        }
    }

    public static class Builder {
        Provider provider = new Provider("*", "*");
        
        Set<String> packages = new HashSet<>();
        
        Builder with(String groupId, String artifactId) {
            provider = new Provider(groupId, artifactId);
            return this;
        }
        
        public Builder with(Provider thatProvider) {
            provider = thatProvider;
            return this;
        }
        
        public Builder addPackages(String[] names) {
            packages.addAll(Arrays.asList(names));
            return this;
        }
        
        public ForeignExtensionPackages build() {
            return new ForeignExtensionPackages(provider, packages);
        }
    }
    
    public static final String ANNOTATION_PATH = "noname.maven.devenv.spi.plexus.ForeignExtensionPackages.Import";
    

    
    public record Provider(String groupId, String artifactId) {

    }

}


