package su.demands.d4jdframework.command;

import org.reactivestreams.Publisher;
import su.demands.d4jdframework.command.executor.Executor;

public abstract class CommandHandle {
    public abstract Publisher<?> execute(Executor executor, String... arguments);
}
