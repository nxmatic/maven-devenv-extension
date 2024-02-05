package noname.maven.devenv.internal.lifecycle;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.logging.Logger;

import io.vavr.control.Try;
import noname.maven.devenv.spi.DevenvComponent;
import noname.maven.devenv.spi.plexus.MultiModuleProjectLifecycleParticipant.Actions;
import noname.maven.devenv.spi.plexus.MultiModuleProjectLifecycleParticipant.Listener;
import noname.maven.devenv.spi.plexus.MultiModuleProjectLifecycleParticipant.Registration;

@Named(DevenvComponent.HINT)
@Singleton
public class MultiModuleProjectEventSpy extends AbstractEventSpy implements DevenvComponent {

    public void onEvent(final Object event) throws Exception {
        if (!(event instanceof ExecutionEvent)) {
            return;
        }

        final ExecutionEvent ee = (ExecutionEvent) event;
        final ExecutionEvent.Type type = ee.getType();
        final MavenSession mavenSession = ee.getSession();

        Optional.of(mavenSession.getRequest())
                .map(MavenExecutionRequest::getMultiModuleProjectDirectory)
                .ifPresent(pomFile -> {
                    final State state = states.computeIfAbsent(pomFile, State::new);
                    switch (type) {
                        case ProjectDiscoveryStarted:
                            state.init(mavenSession);
                            break;
                        case SessionEnded:
                            state.finish(mavenSession);
                            break;
                        default:
                            break;
                    }
                });
    }

    @Inject
    Logger logger;

    final Map<File, State> states = new ConcurrentHashMap<>();

    List<Registration> registrations = Collections.emptyList();

    @Inject
    public void inject(List<Registration> registrations) {
        this.registrations = registrations.stream()
                .sorted((a1, a2) -> Integer.compare(getPriority(a2), getPriority(a1)))
                .collect(Collectors.toList());
    }

    private static <T extends Exception> Integer getPriority(Registration registration) {
        return registration.getPriority();
    }

    class State {

        final AtomicInteger inUse = new AtomicInteger(0);

        final ReentrantLock lock = new ReentrantLock();

        final File directory;

        public State(File directory) {
            this.directory = directory;
        }

        @FunctionalInterface
        interface CheckedBiConsumer<T1, T2> {
            void accept(T1 t1, T2 t2) throws Exception;

            default BiConsumer<T1, T2> unchecked() {
                return (t1, t2) -> {
                    try {
                        accept(t1, t2);
                    } catch (Throwable t) {
                        sneakyThrow(t);
                    }
                };
            }

            static <T1, T2> CheckedBiConsumer<T1, T2> of(CheckedBiConsumer<T1, T2> methodReference) {
                return methodReference;
            }
        }

        @SuppressWarnings("unchecked")
        static <T extends Throwable, R> R sneakyThrow(Throwable t) throws T {
            throw (T) t;
        }

        public void init(MavenSession mavenSession) {
            process(listener -> listener.onInitializing(mavenSession),
                    actions -> CheckedBiConsumer.of(Actions::init).unchecked().accept(actions, mavenSession),
                    (listener, attempt) -> attempt.andThen(() -> listener.onInitialized(mavenSession, Optional.empty()))
                                                  .onFailure(Exception.class,
                                                          e -> listener.onInitialized(mavenSession, Optional.of(e))),
                    "Failed to initialize ");

        }

        public void finish(MavenSession mavenSession) {
            process(listener -> listener.onInitializing(mavenSession),
                    actions -> CheckedBiConsumer.of(Actions::finish).unchecked().accept(actions, mavenSession),
                    (listener, attempt) -> attempt.andThen(() -> listener.onInitialized(mavenSession, Optional.empty()))
                                                  .onFailure(Exception.class,
                                                          e -> listener.onInitialized(mavenSession, Optional.of(e))),
                    "Failed to finish ");
        }

        void process(
                Consumer<Listener> onBefore,
                Consumer<Actions> applier,
                BiConsumer<Listener, Try<Void>> onAfter,
                String errorMessage) {
            if (inUse.getAndIncrement() > 0) {
                return;
            }
            lockAndAccept(applier,
                    listener -> onBefore.accept(listener),
                    onAfter.andThen((__, attempt) -> {
                        attempt.onFailure(e -> logger.error(errorMessage, e));
                    }));
        }

        void lockAndAccept(
                Consumer<Actions> applier,
                Consumer<Listener> onBefore,
                BiConsumer<Listener, Try<Void>> onAfter) {
            lock.lock();
            try {
                registrations.forEach(registration -> accept(registration, onBefore, applier, onAfter));
            } finally {
                lock.unlock();
            }
        }

        void accept(
                Registration registration,
                Consumer<Listener> onBefore,
                Consumer<Actions> applier,
                BiConsumer<Listener, Try<Void>> onAfter) {
            Listener listener = registration.listener();
            onBefore.accept(listener);
            onAfter.accept(listener, Try.run(() -> applier.accept(registration.actions())));
        }

    }

}
