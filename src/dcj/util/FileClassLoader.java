package dcj.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A concrete StreamClassLoader that loads a class from a .class file on disk.
 *
 * In the textbook, Farley builds a URLClassLoader (loads classes over HTTP) and
 * then says: "We could implement other subclasses of the StreamClassLoader that
 * use other network protocols ... this, however, is left as an exercise for the
 * reader." FileClassLoader is exactly that kind of subclass, using the local
 * filesystem as the source instead of HTTP - and it is easy to run.
 *
 * Class-locator format:   <fully.qualified.ClassName>@<path/to/File.class>
 *
 * Try it (after compiling the project so bin/ exists):
 *   java dcj.util.FileClassLoader \
 *        "dcj.examples.networking.InetAddressDemo@bin/dcj/examples/networking/InetAddressDemo.class"
 */
public class FileClassLoader extends StreamClassLoader {

    @Override
    protected String parseClassName(String classLocator) throws ClassNotFoundException {
        int at = classLocator.indexOf('@');
        if (at <= 0) {
            throw new ClassNotFoundException(
                    "Locator must be <fully.qualified.Name>@<path-to-.class>");
        }
        return classLocator.substring(0, at);
    }

    @Override
    protected byte[] readClassBytes(String classLocator) throws IOException {
        int at = classLocator.indexOf('@');
        String path = classLocator.substring(at + 1);
        return Files.readAllBytes(Path.of(path));   // read the bytecodes
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: FileClassLoader <fully.qualified.Name>@<path-to-.class>");
            return;
        }
        try {
            FileClassLoader loader = new FileClassLoader();
            Class<?> c = loader.loadFromLocator(args[0], true);
            System.out.println("Loaded class : " + c.getName());
            System.out.println("Defined by   : " + c.getClassLoader());
            // Prove it is usable: construct an instance via its no-arg constructor.
            Object instance = c.getDeclaredConstructor().newInstance();
            System.out.println("Created instance: " + instance);
        } catch (ClassNotFoundException e) {
            // More specific: must be caught before ReflectiveOperationException.
            System.out.println(e.getMessage());
        } catch (ReflectiveOperationException e) {
            System.out.println("Loaded the class, but could not instantiate it: " + e);
        }
    }
}
