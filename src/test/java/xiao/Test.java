package xiao;

import xiao.EA;
import xiao.λ.Env;

import static java.lang.String.*;
import static xiao.CodeGen.*;
import static xiao.λ.*;
import static xiao.λ.FFI.*;
import static xiao.λ.Names.LAMBDA;
import static xiao.λ.Primitives.*;

/**
 * @author chuxiaofeng
 */
public interface Test {

    static void exit() {
        System.exit(0);
    }
    static Closure ec(String s) {
        return λ.eval(compile(s));
    }

    static FFI.F cc(String s) {
        return λ.compile(λ.compile(s));
    }


    // natify eval
    static int ne(String s) {
        int i = λ.natify(cc(s));
        System.out.printf("console.assert(%d === ((%s)(n => n + 1)(0)), `%s`)\n\n", i, js(compile(s)), s);
        return i;
    }
    // boolify eval
    static boolean be(String s) {
        boolean b = λ.boolify(cc(s));
        System.out.printf("console.assert((%s)(_ => true)(_ => false), `%s`)\n\n", js(compile(s)), s);
        return b;
    }
    // listify natify eval
    static FFI.Pair<Integer> lne(String s) {
        Pair<Integer> lst = λ.natListify(cc(s));
        System.out.printf("console.assert(JSON.stringify([3, [4, null]]) === JSON.stringify((() => { let unchurchify = (churched) => churched(car => cdr => [car(n => n+1)(0), unchurchify(cdr)])(nil => null); return unchurchify })()(%s)), %s)\n\n", js(compile(s)), s);
        return lst;
    }
    // listify boolify eval
    static Pair<Boolean> lbe(String s) {
        return λ.boolListify(cc(s));
    }
    static String num(int n) {
        return json(compile(n + ""));
    }


    static void test() {

        String not = "[['λ', ['not'], ['not', %s]], ['λ', ['cond'], ['if', 'cond', '#f', '#t']]]";

        assert 0 == ne(format("[%s, %s]", S_PRED, num(0)));
        assert 8 == ne(format("[%s, %s]", S_PRED, num(9)));
        assert 9 == ne(format("[%s, %s]", S_SUCC, num(8)));

        assert 11 == ne(format("[%s, %s, %s]", S_SUM, num(5), num(6)));
        assert 0 == ne(format("[%s, %s, %s]", S_SUB, num(0), num(1)));

        assert 0 == ne("['+', 0, 0]");
        assert 1 == ne("['+', 0, 1]");
        assert 1 == ne("['+', 1, 0]");
        assert 7 == ne("['+', 3, 4]");

        assert 0 == ne("['-', 0, 0]");
        assert 0 == ne("['-', 0, 1]");
        assert 2 == ne("['-', 3, 1]");
        assert 1 == ne("['-', 3, 2]");

        assert 0 == ne("['*', 0, 0]");
        assert 0 == ne("['*', 0, 2]");
        assert 0 == ne("['*', 2, 0]");
        assert 12 == ne("['*', 3, 4]");

        assert 7 == ne("['let', [['x', 3], ['y', 4]], ['+', 3, 4]]");

        assert be("['=', 3, 3]");
        // assert !be("['=', 3, 4]");
        assert be(format(not, "['=', 3, 4]"));

        assert Pair.of(3, 4).equals(lne("['cons', 3, ['cons', 4, ['quote', []]]]"));
        assert 3 == ne("['car', ['cons', 3, 4]]");
        assert 4 == ne("['cdr', ['cons', 3, 4]]");

        // assert !be("['null?', ['cons', 3, 4]]");
        assert be(format(not, "['null?', ['cons', 3, 4]]"));
        assert be("['pair?', ['cons', 3, 4]]");
        assert be("['null?', ['quote', []]]");

        assert be("['or', '#t', '#f']");
        assert be("['or', '#f', '#t']");
        assert be(format(not, "['or', '#f', '#f']"));

        assert 120 == ne(fact);
    }

    static void test1() {
        assert "(λ (+) ((+ (λ (f) (λ (z) z))) (λ (f) (λ (z) z))))" .equals(scheme(compile("['λ', ['+'],  ['+', 0, 0]]")));

        {
            Env<Expr> env = compilerEnv();
            env.put(Sym.of("x"), compile("'#t'"));
            env.put(Sym.of("y"), compile("'#f'"));
            env.put(Sym.of("z"), compile("'#t'"));
            env.put(Sym.of("b"), compile("'#t'"));
            env.put(Sym.of("c"), compile("'#t'"));
            System.err.println(scheme(compile("['if', ['and', 'x', ['or', 'y', 'z']], 'b', 'c']", env)));
        }
    }

    static void testPrinter() {
        Expr s = compile(fact);
        System.err.println(js(s));
        System.err.println(py(s));
        System.err.println(json(s));
        System.err.println(scheme(s));
    }

    // (letrec [(f (λ (n) (if (= n 0) 1 (* n (f (- n 1))))))] (f 5))
    String fact =
            "['letrec', [\n" +
                    "            ['f', \n" +
                    "              ['" + LAMBDA + "', ['n'],\n" +
                    "                  ['if', ['=', 'n', 0],\n" +
                    "                        1,\n" +
                    "                        ['*', 'n', ['f', ['-', 'n', 1]]]]]]],\n" +
                    " ['f', 5]]";


    static void main(String[] args) {
        EA.main(Test.class, args, n -> n.startsWith(Test.class.getPackage().getName()));
        test();
        test1();
        testPrinter();
    }
}
