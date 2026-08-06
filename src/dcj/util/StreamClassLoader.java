package dcj.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * TOOL: java.lang.ClassLoader  (loading classes at runtime from anywhere)
 *
 * The ClassLoader lets an agent read the bytecodes making up a class definition
 * from some source (a file, a URL, a socket) and create objects of that class
 * inside its own process. This is how a browser loads applet classes over HTTP.
 *
 * StreamClassLoader is a generic base for "load a class from some stream/source"
 * loaders. Subclasses define how to turn a class LOCATOR (a URL, a file path,
 * ...) into (a) the class name and (b) the raw bytecodes.
 *
 * Modernization note vs. the 1998 textbook:
 *   - The old protected method defineClass(byte[], int, int) was removed from the
 *     JDK; the current form defineClass(String name, byte[] b, int off, int len)
 *     is used here.
 *   - We expose loadFromLocator(...) instead of overriding loadClass(...), so we
 *     don't disturb the JVM's normal parent-delegation model.
 */
public abstract class StreamClassLoader extends ClassLoader {

    private final Map<String, Class<?>> classCache = new HashMap<>();

    /** Parse the class name out of a class locator (URL, filename, etc.). */
    protected abstract String parseClassName(String classLocator) throws ClassNotFoundException;

    /** Read the raw bytecodes for the class addressed by the locator. */
    protected abstract byte[] readClassBytes(String classLocator)
            throws IOException, ClassNotFoundException;

    /**
     * Load (and optionally resolve) the class addressed by the given locator.
     * The first argument is a class LOCATOR, not just a class name.
     */
    public Class<?> loadFromLocator(String classLocator, boolean resolve)
            throws ClassNotFoundException {

        String className = parseClassName(classLocator);
        Class<?> c = classCache.get(className);

        if (c == null) {
            byte[] data;
            try {
                data = readClassBytes(classLocator);
            } catch (IOException e) {
                throw new ClassNotFoundException("Failed reading class from source: " + e);
            }
            try {
                c = defineClass(className, data, 0, data.length);
            } catch (ClassFormatError e) {
                throw new ClassNotFoundException("Format error in class data for " + className);
            }
            classCache.put(className, c);
        }

        if (resolve) {
            resolveClass(c);
        }
        return c;
    }
}
