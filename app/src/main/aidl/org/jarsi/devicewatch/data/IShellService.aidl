package org.jarsi.devicewatch.data;

/** What ShellUserService, running as the ADB shell user under Shizuku, offers the app. */
interface IShellService {
    /** The transaction Shizuku itself sends to end the service. */
    void destroy() = 16777114;

    /**
     * Starts a shell and returns its two ends: [0] writes to its input, [1] reads
     * its output. Closing [0] ends the shell.
     */
    ParcelFileDescriptor[] openShell() = 1;
}
