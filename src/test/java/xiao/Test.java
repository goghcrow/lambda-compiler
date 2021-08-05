package xiao;

import xiao.λ.*;
import xiao.λ.UnChurchification.Pair;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static xiao.λ.CodeGen.*;
import static xiao.λ.Expr.*;
import static xiao.λ.Parser.Node.*;
import static xiao.λ.Primitives.*;
import static xiao.λ.*;

/**
 * @author chuxiaofeng
 */
@SuppressWarnings("SameParameterValue")
public class Test {

    public static void main(String[] args) {
        runMainWithEnableAssert(Test.class, args, n -> n.startsWith(Test.class.getPackage().getName()));
        Test test = new Test();
        test.hello();
        test.test();
        test.fizzbuzz();
        test.fact();
        test.size();
        test.tmp();
        System.out.println(test.jsCode);
    }


    String jsCode = "";

    void assertEquals(Pair<Integer> expected, String jsArr, String s) {
        assert expected.equals(compile(s, java).list(UnChurchification::natify));
        jsCode += format("console.assert(JSON.stringify(%s) === JSON.stringify((() => { let unchurchify = (churched) => churched(car => cdr => [car(n => n+1)(0), unchurchify(cdr)])(nil => null); return unchurchify })()(%s)), `%s`)\n\n", jsArr, compile(s, js), s);
    }

    void assertEquals(String expected, String s) {
        assert expected.equals(compile(s, java).string());
        // console.assert(`%s`
        jsCode += format("console.assert(`%s`=== (() => { let unchurchify = (churched) => churched(car => cdr => String.fromCharCode(car(n => n+1)(0)) + unchurchify(cdr))(nil => ''); return unchurchify })()(%s), %s)\n\n", expected, compile(s, js), s);
    }

    void assertEquals(int expected, String s) {
        assert expected == compile(s, java).nat();
        jsCode += format("console.assert(%d === ((%s)(n => n + 1)(0)), `%s`)\n\n", expected, compile(s, js), s);
    }

    void assertTrue(String s) {
        assert compile(s, java).bool();
        jsCode += format("console.assert((%s)(_ => true)(_ => false), `%s`)\n\n", compile(s, js), s);
    }

    void assertFalse(String s) {
        assert !compile(s, java).bool();
        jsCode += format("console.assert(false === (%s)(_ => true)(_ => false), `%s`)\n\n", compile(s, js), s);
    }


    void hello() {
        assertEquals("Hello World!", "\"Hello World!\"");
    }

    void test() {
        assertEquals(0, format("(%s %s)", S_PRED, num(0)));
        assertEquals(8, format("(%s %s)", S_PRED, num(9)));
        assertEquals(9, format("(%s %s)", S_SUCC, num(8)));

        assertEquals(11, format("(%s %s %s)", S_SUM, num(5), num(6)));
        assertEquals(0, format("(%s %s %s)", S_SUB, num(0), num(1)));

        assertTrue("(= 0 0)");
        assertTrue("(= 1 1)");
        assertTrue("(= 2 2)");

        assertTrue("(!= 1 0)");
        assertTrue("(!= 1 2)");

        assertEquals(0, "(+ 0 0)");
        assertEquals(1, "(+ 0 1)");
        assertEquals(1, "(+ 1 0)");
        assertEquals(7, "(+ 3 4)");

        assertEquals(0, "(- 0 0)");
        assertEquals(0, "(- 0 1)");
        assertEquals(2, "(- 3 1)");
        assertEquals(1, "(- 3 2)");

        assertEquals(0, "(* 0 0)");
        assertEquals(0, "(* 0 2)");
        assertEquals(0, "(* 2 0)");
        assertEquals(12, "(* 3 4)");

        // assertEquals(0, "(/ 1 0)"); // ERROR: stackoverflow
        // assertEquals(0, "(/ 0 0)"); // ERROR: stackoverflow
        assertEquals(0, "(/ 0 2)");
        assertEquals(0, "(/ 3 4)");
        assertEquals(3, "(/ 3 1)");
        assertEquals(1, "(/ 3 2)");
        assertEquals(1, "(/ 3 3)");

        assertEquals(1, "(^ 0 0)");
        assertEquals(0, "(^ 0 2)");
        assertEquals(1, "(^ 2 0)");
        assertEquals(8, "(^ 2 3)");

        assertFalse("(not #t)");
        assertTrue("(not #f)");

        assertTrue("(<= 0 0)");
        assertTrue("(<= 0 1)");
        assertTrue("(<= 3 4)");
        assertTrue("(<= 3 3)");
        assertFalse("(<= 1 0)");
        assertFalse("(<= 4 3)");

        assertFalse("(> 0 0)");
        assertFalse("(> 0 1)");
        assertFalse("(> 3 4)");
        assertFalse("(> 3 3)");
        assertTrue("(> 1 0)");
        assertTrue("(> 4 3)");

        assertTrue("(>= 0 0)");
        assertTrue("(>= 1 0)");
        assertTrue("(>= 4 3)");
        assertTrue("(>= 3 3)");
        assertFalse("(>= 0 1)");
        assertFalse("(>= 3 4)");

        assertFalse("(< 0 0)");
        assertFalse("(< 1 0)");
        assertFalse("(< 4 3)");
        assertFalse("(< 3 3)");
        assertTrue("(< 0 1)");
        assertTrue("(< 3 4)");

        // assertEquals(0, "(% 0 0)"); // ERROR: stackoverflow
        assertEquals(0, "(% 0 1)");
        assertEquals(0, "(% 0 2)");
        assertEquals(1, "(% 1 2)");
        assertEquals(2, "(% 2 3)");
        assertEquals(0, "(% 4 2)");
        assertEquals(1, "(% 4 3)");
        assertEquals(1, "(% 10 3)");

        assertEquals(7, "(let ((x 3) (y 4)) (+ 3 4))");

        assertTrue("(= 3 3)");
        assertFalse("(= 3 4)");

        assertEquals(Pair.of(3, 4), "[3, [4, null]]", "(cons 3 (cons 4 (quote ())))");
        assertEquals(3, "(car (cons 3 4))");
        assertEquals(4, "(cdr (cons 3 4))");

        assertFalse("(null? (cons 3 4))");
        assertTrue("(pair? (cons 3 4))");
        assertTrue("(null? (quote ()))");

        assertTrue("(or #t #f)");
        assertTrue("(or #f #t)");
        assertFalse("(or #f #f)");

        // 验证替换不会把body的+ 无脑换了
        assert "(λ (+) ((+ (λ (f) (λ (z) z))) (λ (f) (λ (z) z))))" .equals(compile("(λ (+)  (+ 0 0))", scheme));
    }

    void fizzbuzz() {
        String fizzbuzz = "(letrec ((fizzbuzz (λ (i s)\n" +
                "   (if (<= i 100)\n" +
                "       (if (= (% i 15) 0)\n" +
                "           (fizzbuzz (+ i 1) (cons \"FizzBuzz\" s))\n" +
                "           (if (= (% i 3) 0)\n" +
                "               (fizzbuzz (+ i 1) (cons \"Fizz\" s))\n" +
                "               (if (= (% i 5) 0)\n" +
                "                   (fizzbuzz (+ i 1) (cons \"Buzz\" s))\n" +
                "                   (fizzbuzz (+ i 1) (cons \n" +
                "                                           (if (< i 10) \n" +
                "                                                   (cons (+ 48 i) (quote ())) \n" +
                "                                                   (cons (+ 48 (/ i 10)) (cons (+ 48 (% i 10)) (quote ())))) \n" +
                "                                           s))\n" +
                "                   )\n" +
                "               )\n" +
                "           )\n" +
                "       s))))\n" +
                "      (fizzbuzz 1 (quote ())))";
        Pair<String> s = compile(fizzbuzz, java).list(UnChurchification::stringify);
        List<String> lst = s.list();
        Collections.reverse(lst);
        System.err.println(lst);
    }


    void fact() {
        String fact5 = "(letrec (" +
                "               (fact (λ (n) " +
                "                   (if (= n 0) 1 (* n (fact (- n 1))))))) " +
                "       (fact 5))";
        System.err.println(compile(fact5, js));
        System.err.println(compile(fact5, py));
        System.err.println(compile(fact5, json));
        System.err.println(compile(fact5, scheme));

        assertEquals(120, fact5);
    }

    void size() {
        String size = "(letrec ((size (λ (s) (if (null? s) 0 (+ 1 (size (cdr s))))))) (size %s))";

        assertEquals(3, format(size, cons(3, 4, 5)));
        assertEquals(2, format(size, cons(3, 4)));
        assertEquals(0, format(size, cons()));
    }

    void tmp() {
        Env<Expr> env = bootEnv();
        env.put(symOf("x"), compile("#t"));
        env.put(symOf("y"), compile("#f"));
        env.put(symOf("z"), compile("#t"));
        env.put(symOf("b"), compile("#t"));
        env.put(symOf("c"), compile("#t"));
        System.err.println(compile("(if (and x (or y z)) b c)", env, CodeGen.scheme, null));
    }



    static String num(int n) {
        return compile(n + "", scheme);
    }

    static String cons(Object ...args) {
        return cons(Arrays.asList(args));
    }

    static String cons(List<?> els) {
        if (els.isEmpty()) {
            return "(quote ())";
        } else {
            return "(cons " + els.get(0) + " " + cons(els.subList(1, els.size())) + ")";
        }
    }


    /**
     * idea 动态开始 enable assert, 不用配置 -ea
     */
    static void runMainWithEnableAssert(Class<?> k, String[] args, Predicate<String> filter) {
        if (k.desiredAssertionStatus()) {
            return;
        }

        URL[] urls = ((URLClassLoader) k.getClassLoader()).getURLs();
        ClassLoader appCacheCl = ClassLoader.getSystemClassLoader();
        ClassLoader extNoCacheCl = appCacheCl.getParent();
        URLClassLoader cl = new URLClassLoader(urls, extNoCacheCl) {
            @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (filter.test(name)) {
                    setClassAssertionStatus(name, true);
                }
                return super.loadClass(name);
            }
        };

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
