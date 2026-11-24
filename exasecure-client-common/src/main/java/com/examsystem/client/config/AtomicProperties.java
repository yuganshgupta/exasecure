package com.examsystem.client.config;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/**
 * Account-private properties, written completely before atomic replacement.
 * POSIX: 0700 directories/0600 files. Windows: owner-only ACL, read back before writing.
 * Unsupported permissions or atomic replacement fail safely, retaining the previous file.
 * No crash/power-loss durability guarantee for the containing directory, or isolation from
 * administrators or other programs running as the same OS account.
 */
public final class AtomicProperties {
    private AtomicProperties() { }

    public static void write(Path destination, Properties values) throws IOException {
        write(destination, values, (source, target) -> Files.move(source, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    @FunctionalInterface
    interface Replacement { void move(Path source, Path destination) throws IOException; }

    static void write(Path destination, Properties values, Replacement replacement) throws IOException {
        Path file = destination.toAbsolutePath().normalize();
        Path parent = file.getParent();
        createParents(parent);
        restrict(parent, true, false);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Configuration destination must be a regular file.");
            }
            verifyOwner(file);
        }
        Path temp = Files.createTempFile(parent, ".config-", ".tmp", attributes(parent, false));
        try {
            restrict(temp, false, true);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                OutputStream output = Channels.newOutputStream(channel);
                values.store(output, "ExaSecure configuration");
                output.flush();
                channel.force(true);
            }
            try {
                replacement.move(temp, file);
            } catch (AtomicMoveNotSupportedException e) {
                // A non-atomic REPLACE_EXISTING can destroy the old file on failure. Do not risk it.
                throw new IOException("This filesystem cannot safely replace configuration atomically; use a local filesystem. Previous settings retained.");
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void verifyOwner(Path file) throws IOException {
        UserPrincipal current = file.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            if (!posix.readAttributes().owner().equals(current)) {
                throw new IOException("Configuration file must be owned by the current OS account.");
            }
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl != null) {
            if (!isAccountOwner(acl.getOwner(), current, file.getFileSystem().getUserPrincipalLookupService())) {
                throw new IOException("Configuration file must be owned by the current OS account.");
            }
        }
    }

    private static void createParents(Path directory) throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Configuration parent must be a directory, not a symbolic link.");
            }
            // Check ancestors without changing permissions of the user's home or system directories.
            for (Path p = directory.getParent(); p != null; p = p.getParent()) {
                if (Files.isSymbolicLink(p)) throw new IOException("Configuration parent contains a symbolic link.");
            }
            return;
        }
        createParents(directory.getParent());
        boolean created = false;
        try {
            Files.createDirectory(directory, attributes(directory.getParent(), true));
            created = true;
        } catch (FileAlreadyExistsException e) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw e;
        }
        restrict(directory, true, created);
    }

    private static FileAttribute<?>[] attributes(Path existing, boolean directory) throws IOException {
        if (Files.getFileStore(existing).supportsFileAttributeView(PosixFileAttributeView.class)) {
            return new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"))};
        }
        if (Files.getFileStore(existing).supportsFileAttributeView(AclFileAttributeView.class)) {
            UserPrincipal owner = existing.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
            List<AclEntry> acl = ownerAcl(owner);
            return new FileAttribute<?>[]{new FileAttribute<List<AclEntry>>() {
                public String name() { return "acl:acl"; }
                public List<AclEntry> value() { return acl; }
            }};
        }
        throw new IOException("Filesystem cannot enforce owner-only configuration permissions.");
    }

    private static List<AclEntry> ownerAcl(UserPrincipal owner) {
        return List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build());
    }

    private static void restrict(Path path, boolean directory, boolean created) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            var permissions = PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------");
            posix.setPermissions(permissions);
            if (!posix.readAttributes().permissions().equals(permissions)) throw new IOException("Owner-only permissions could not be verified.");
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl == null) throw new IOException("Filesystem cannot enforce owner-only configuration permissions.");
        UserPrincipalLookupService lookup = path.getFileSystem().getUserPrincipalLookupService();
        UserPrincipal current = lookup.lookupPrincipalByName(System.getProperty("user.name"));
        if (created && !acl.getOwner().equals(current)) {
            try {
                acl.setOwner(current);
            } catch (IOException | SecurityException ignored) {
            }
        }
        if (!isAccountOwner(acl.getOwner(), current, lookup)) {
            throw new IOException("Configuration must be owned by the current OS account.");
        }
        List<AclEntry> expected = ownerAcl(current);
        acl.setAcl(expected);
        if (!acl.getAcl().equals(expected)) throw new IOException("Owner-only ACL could not be verified.");
    }

    private static boolean isAccountOwner(UserPrincipal owner, UserPrincipal current, UserPrincipalLookupService lookup) {
        if (owner.equals(current)) {
            return true;
        }
        if (isWindowsAdmin()) {
            try {
                UserPrincipal adminGroup = lookup.lookupPrincipalByName("BUILTIN\\Administrators");
                if (owner.equals(adminGroup)) {
                    return true;
                }
            } catch (IOException ignored) {
            }
            String name = owner.getName();
            return name.equalsIgnoreCase("BUILTIN\\Administrators") || name.equalsIgnoreCase("Administrators");
        }
        return false;
    }

    private static boolean isWindowsAdmin() {
        try {
            com.sun.security.auth.module.NTSystem nt = new com.sun.security.auth.module.NTSystem();
            String[] groups = nt.getGroupIDs();
            if (groups != null) {
                for (String sid : groups) {
                    if ("S-1-5-32-544".equals(sid)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
