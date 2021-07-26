package xiao;

import xiao.λ.*;
import xiao.λ.UnChurchification.Pair;

import java.util.Arrays;
import java.util.List;

import static java.lang.String.format;
import static xiao.λ.CodeGen.*;
import static xiao.λ.*;
import static xiao.λ.Names.LAMBDA;
import static xiao.λ.Primitives.*;
import static xiao.λ.UnChurchification.Pair;

/**
 * @author chuxiaofeng
 */
public class Test {

    void exit() {
        System.exit(0);
    }

    String jsCode = "";

    // natify eval
    int ne(String s) {
        int i = compile(s, java).nat();
        jsCode += format("console.assert(%d === ((%s)(n => n + 1)(0)), `%s`)\n\n", i, compile(s, js), s);
        return i;
    }
    // boolify eval
    boolean be(String s) {
        boolean b = compile(s, java).bool();
        jsCode += format("console.assert((%s)(_ => true)(_ => false), `%s`)\n\n", compile(s, js), s);
        return b;
    }
    // listify natify eval
    Pair<Integer> lne(String s) {
        Pair<Integer> lst = compile(s, java).natList();
        jsCode += format("console.assert(JSON.stringify([3, [4, null]]) === JSON.stringify((() => { let unchurchify = (churched) => churched(car => cdr => [car(n => n+1)(0), unchurchify(cdr)])(nil => null); return unchurchify })()(%s)), %s)\n\n", compile(s, js), s);
        return lst;
    }
    // listify boolify eval
    Pair<Boolean> lbe(String s) {
        return compile(s, java).boolList();
    }
    String num(int n) {
        return compile(n + "", json);
    }

    String cons(Object ...args) {
        return cons(Arrays.asList(args));
    }
    String cons(List els) {
        if (els.isEmpty()) {
            return "['quote', []]";
        } else {
            return "['cons', " + els.get(0) + ", " + cons(els.subList(1, els.size())) + "]";
        }
    }



    @SuppressWarnings("AssertWithSideEffects")
    void test() {
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

        assert 1 == ne("['^', 0, 0]");
        assert 0 == ne("['^', 0, 2]");
        assert 1 == ne("['^', 2, 0]");
        assert 8 == ne("['^', 2, 3]");


        String le = "['letrec', [['less_or_eq', ['λ', ['m', 'n'], ['zero?', ['-', 'm', 'n']]]]], ['less_or_eq', %d, %d]]";
        assert be(format(le, 0, 0));
        assert be(format(le, 0, 1));
        assert be(format(le, 3, 4));
        assert be(format(le, 3, 3));
        assert be(format(not, format(le, 1, 0)));
        assert be(format(not, format(le, 4, 3)));



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

        assert 3 == ne(format(size, cons(3, 4, 5)));
        assert 2 == ne(format(size, cons(3, 4)));
        assert 0 == ne(format(size, cons()));

        System.out.println(jsCode);
        jsCode = "";
    }

    // (letrec [(f (λ (n) (if (= n 0) 1 (* n (f (- n 1))))))] (f 5))
    final String fact =
            "['letrec', [\n" +
                    "            ['f', \n" +
                    "              ['λ', ['n'],\n" +
                    "                  ['if', ['=', 'n', 0],\n" +
                    "                        1,\n" +
                    "                        ['*', 'n', ['f', ['-', 'n', 1]]]]]]],\n" +
                    " ['f', 5]]";
    final String size = "['letrec', [\n" +
            "  ['size', ['λ', ['s'], ['if', ['null?', 's'], 0, ['+', 1, ['size', ['cdr', 's']]]]]]],\n" +
            "  ['size', %s]]";



    void tmp() {
        assert "(λ (+) ((+ (λ (f) (λ (z) z))) (λ (f) (λ (z) z))))" .equals(compile("['λ', ['+'],  ['+', 0, 0]]", scheme));

        {
            Env<Expr> env = bootEnv();
            env.put(Sym.of("x"), compile("'#t'"));
            env.put(Sym.of("y"), compile("'#f'"));
            env.put(Sym.of("z"), compile("'#t'"));
            env.put(Sym.of("b"), compile("'#t'"));
            env.put(Sym.of("c"), compile("'#t'"));

            System.err.println(compile("['if', ['and', 'x', ['or', 'y', 'z']], 'b', 'c']", env, CodeGen.scheme, null));
        }

        System.err.println(compile(fact, js));
        System.err.println(compile(fact, py));
        System.err.println(compile(fact, json));
        System.err.println(compile(fact, scheme));

    }



    public static void main(String[] args) {
        EA.main(Test.class, args, n -> n.startsWith(Test.class.getPackage().getName()));
        Test test = new Test();
        test.test();
        test.tmp();

        System.out.println(compile("['^', 2, 3]", java).nat());
    }
}
