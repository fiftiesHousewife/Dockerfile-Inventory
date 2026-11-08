package io.github.fiftieshousewife.dockerfile.model;

/** One Dockerfile instruction after {@code FROM}. Lists and maps are copied, so a built model cannot change. */
public sealed interface Instruction
        permits Arg, Env, Label, Run, Copy, Add, CopyHeredoc, Healthcheck, Onbuild, User, Workdir, Expose, Entrypoint,
        Cmd, Shell, Volume, StopSignal, Maintainer, Raw {
}
