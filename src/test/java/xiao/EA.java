package xiao;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.Predicate;

/**
 * enable assert -ea
 * @author chuxiaofeng
 */
public interface EA {

    class EAClassLoader extends URLClassLoader {
        final Predicate<String> filter;
        public EAClassLoader(URL[] urls, ClassLoader parent, Predicate<String> filter) {
            super(urls, parent);
            this.filter = filter;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (filter.test(name)) {
                setClassAssertionStatus(name, true);
            }
            return super.loadClass(name);
        }
    }

    /**
     * idea 不用人肉加 -ea
     */
    static void main(Class<?> k, String[] args, Predicate<String> filter) {
        if (k.desiredAssertionStatus()) {
            return;
        }

        URL[] urLs = ((URLClassLoader) k.getClassLoader()).getURLs();
        ClassLoader appCacheCl = ClassLoader.getSystemClassLoader();
        ClassLoader extNoCacheCl = appCacheCl.getParent();
        URLClassLoader cl = new EAClassLoader(urLs, extNoCacheCl, filter);
        try {
            cl.setClassAssertionStatus(k.getName(), true);
            Method main = cl.loadClass(k.getName()).getDeclaredMethod("main", String[].class);
            main.invoke(null, new Object[] { args } );
        } catch (InvocationTargetException e) {
            e.getTargetException().printStackTrace(System.err);
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }
}

