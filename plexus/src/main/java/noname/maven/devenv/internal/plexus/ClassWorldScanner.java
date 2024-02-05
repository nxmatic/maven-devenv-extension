package noname.maven.devenv.internal.plexus;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.classrealm.ClassRealmManager;
import org.apache.maven.classrealm.DefaultClassRealmManager;
import org.apache.maven.extension.internal.CoreExports;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;

import com.google.inject.ProvisionException;

import io.vavr.CheckedFunction1;
import io.vavr.control.Try;
import noname.maven.devenv.spi.DevenvComponent;

@Named(DevenvComponent.HINT)
public class ClassWorldScanner {

    @Inject
    PlexusContainer container;
    
    @Inject
    CoreExports coreExports;
    
    ClassWorld classWorld;

    @Inject
    void injectRealClassWorld(ClassRealmManager classRealmManager) {
        CheckedFunction1<DefaultClassRealmManager, ClassWorld> classWorldOf = manager -> {
            Field worldField = DefaultClassRealmManager.class.getDeclaredField("world");
            worldField.setAccessible(true);
            return (ClassWorld) worldField.get(classRealmManager);
        };
        classWorld = Try.success(classRealmManager)
                .mapTry(DefaultClassRealmManager.class::cast)
                .mapTry(classWorldOf).getOrElseThrow(
                        ex -> new ProvisionException("Failed to inject real class world", ex));
    };

    record Entry(CoreExports coreExports, ClassRealm classRealm,  PlexusContainer container) {
    }

    interface Visitor<T> {
        T visit(Entry entry);
    }

    <T> Stream<T> accept(Visitor<T> visitor) {
        return classWorld.getRealms().stream()
                .map(this::entryOf)
                .map(visitor::visit);
    }

    Entry entryOf(ClassRealm classRealm) {
        return new Entry(coreExports, classRealm, container);
    }

    ClassRealm get() {
        throw new UnsupportedOperationException();
    }
}
