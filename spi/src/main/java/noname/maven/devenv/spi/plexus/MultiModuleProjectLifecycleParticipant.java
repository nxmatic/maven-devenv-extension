package noname.maven.devenv.spi.plexus;

import java.util.Optional;

import org.apache.maven.execution.MavenSession;

public interface MultiModuleProjectLifecycleParticipant {

    interface Registration {
        Actions actions();

        Listener listener();

        default int getPriority() {
            return PRIORITY_LOW_VALUE;
        }

        final int PRIORIY_HIGHEST_VALUE = 999;

        final int PRIORITY_MEDIUM_VALUE = PRIORIY_HIGHEST_VALUE - 50;

        final int PRIORITY_LOW_VALUE = PRIORIY_HIGHEST_VALUE - 100;
    }

    interface Actions {
        void init(MavenSession session) throws Exception;

        void finish(MavenSession session) throws Exception;
    }

    interface Listener {
        default void onInitializing(MavenSession session) {
        }

        default void onInitialized(MavenSession session, Optional<Exception> attempt) {
        }

        default void onFinishing(MavenSession session) {
        }

        default void onFinished(MavenSession session, Optional<Exception> attempt) {

        }
    }

}
