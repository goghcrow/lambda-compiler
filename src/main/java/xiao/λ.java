package xiao;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import static xiao.λ.Compiler.compile1;
import static xiao.λ.Names.*;
import static xiao.λ.Parser.parse;
import static xiao.λ.Primitives.*;
import static xiao.λ.UnChurchification.*;

/**
 * λ<br>
 * Source to Source Lambda Calculus Compiler <br>
 * 详情参见 README<br>
 *
 * @author chuxiaofeng
 */
@SuppressWarnings("NonAsciiCharacters")
public interface λ {

    static Expr compile(String code) {
        return compile(code, bootEnv(), CodeGen.expr, null);
    }

    static Expr compile(String code, Env<Expr> env) {
        return compile(code, env, CodeGen.expr, null);
    }

    static <Target, Ctx> Target compile(String code, CodeGen<Target, Ctx> gen) {
        return compile(code, bootEnv(), gen, null);
    }

    static <Target, Ctx> Target compile(Expr expr, Visitor<Target, Ctx> to, Ctx toEnv) {
        return to.visit(expr, toEnv);
    }

    static <Target, Ctx> Target compile(String src, Env<Expr> compilerEnv,
                                        Visitor<Target, Ctx> to, Ctx toEnv) {
        return to.visit(Compiler.compile(parse(src), compilerEnv), toEnv);
    }

    static Env<Expr> bootEnv() {
        return bootEnv(Compiler.expander);
    }

    static <T> Env<T> bootEnv(Visitor<T, Env<T>> vis) {
        Map<String, String> primitives = new LinkedHashMap<>();
        primitives.put(VOID, S_VOID);

        // Booleans
        primitives.put(TRUE, S_TRUE);
        primitives.put(FALSE, S_FALSE);
        primitives.put(NOT, S_NOT);

        // Numeral
        primitives.put(IS_ZERO, S_IS_ZERO);
        primitives.put(SUM, S_SUM);
        primitives.put(MUL, S_MUL);
        primitives.put(POW, S_POW);
        primitives.put(SUB, S_SUB);
        primitives.put(EQ, S_EQ);
        primitives.put(NE, S_NE);
        primitives.put(LE, S_LE);
        primitives.put(GE, S_GE);
        primitives.put(LT, S_LT);
        primitives.put(GT, S_GT);
        primitives.put(MOD, S_MOD);
        primitives.put(DIV, S_DIV);

        // Lists
        primitives.put(CONS, S_CONS);
        primitives.put(CAR, S_CAR);
        primitives.put(CDR, S_CDR);
        primitives.put(IS_PAIR, S_IS_PAIR);
        primitives.put(IS_NULL, S_IS_NULL);

        Env<T> env = new Env<>(null);
        primitives.forEach((n, s) -> env.put(Sym.of(n), vis.visit(compile1(parse(s)), env)));
        return env;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /* ----------------------- AST ------------------------ */
    interface Expr {
        default String stringfy() {
            return CodeGen.scheme.visit(this, null);
        }
    }
    class Sym implements Expr {
        final String name;
        private Sym(String name) {
            this.name = name;
        }
        @Override public String toString() {
            return stringfy();
        }
        static final Map<String, Sym> cache = new HashMap<>();
        static Sym of(String name) {
            return cache.computeIfAbsent(name, t -> new Sym(name));
        }
    }
    class App implements Expr {
        final Expr abs;
        final Expr arg;
        App(Expr abs, Expr arg) {
            this.abs = abs;
            this.arg = arg;
        }
        @Override public String toString() {
            return stringfy();
        }
    }
    class Abs implements Expr {
        final Sym param;
        final Expr body;
        Abs(Sym param, Expr body) {
            this.param = param;
            this.body = body;
        }
        @Override public String toString() {
            return stringfy();
        }
    }

    interface Visitor<V, C> {
        V visit(Sym s, C ctx);
        V visit(App s, C ctx);
        V visit(Abs s, C ctx);

        default V visit(Expr s, C ctx) {
            if (s instanceof Sym) {
                return visit(((Sym) s), ctx);
            } else if (s instanceof App) {
                return visit(((App) s), ctx);
            } else if (s instanceof Abs) {
                return visit(((Abs) s), ctx);
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /* ----------------------- Env ---------------------- */
    @SuppressWarnings("MapOrSetKeyShouldOverrideHashCodeEquals")
    class Env<V> {
        final Map<Sym, V> env = new LinkedHashMap<>();
        final /*@Nullable*/ Env<V> parent;

        Env(/*@Nullable*/ Env<V> parent) {
            this.parent = parent;
        }

        // define
        void put(Sym var, V val) {
            assert !env.containsKey(var); // redefine
            env.put(var, val);
        }

        V lookup(Sym sym) {
            V val = env.get(sym);
            if (val != null) {
                return val;
            } else if (parent != null) {
                return parent.lookup(sym);
            } else {
                throw new RuntimeException(sym + " not found");
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /* ---------------------- Parser ------------------------ */
    class Parser {
        static ScriptEngine engine = new ScriptEngineManager().getEngineByName("javascript");

        // String ->  Map | List | String | Integer
        public static Object parse(String s) {
            try {
                return engine.eval("Java.asJSONCompatible(" + s + ")");
            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /* --------------------  Compiler ----------------------- */
    @SuppressWarnings("rawtypes")
    class Compiler {

        static Object Y = parse(S_Y);
        static Object NIL = parse(S_NIL);
        final static Visitor<Expr, Env<Expr>> expander = new Expander();

        // expander 负责把 free var 都替换掉, 保证生成结果 expr 都是 closed term
        static class Expander implements Visitor<Expr, Env<Expr>> {
            @Override public Expr visit(Sym s, Env<Expr> env) { return env.lookup(s); }
            @Override public Expr visit(App s, Env<Expr> env) { return new App(visit(s.abs, env), visit(s.arg, env)); }
            @Override public Expr visit(Abs s, Env<Expr> env) {
                // close term, 干掉 free var
                Env<Expr> subEnv = new Env<>(env);
                subEnv.put(s.param, s.param);
                Expr body = visit(s.body, subEnv);
                return new Abs(s.param, body);
            }
        }

        static Expr compile(Object s, Env<Expr> env) {
            return expander.visit(compile1(s), env);
        }

        // 注意: 不能直接替换代码, 一个case (λ (+) (+ 0 0))
        static Expr compile1(Object exp) {
            if (exp instanceof List) {
                List s = ((List) exp);
                int sz = s.size();
                // if (sz == 0) return Sym.of(NIL);
                assert !s.isEmpty();

                Object car = s.get(0);

                // Lambdas
                if (LAMBDA.equals(car)) {
                    return compileLambda(s);
                }

                // Conditionals
                // and or 需要处理短路, 不能定义成 primitive
                else if (IF.equals(car)) {
                    // [if, cond, then, orElse] => [cond, [lambda, [], then], [lambda, [], orElse]]
                    assert sz == 4;
                    Object cond = s.get(1);
                    List then = list(LAMBDA, list(), s.get(2));
                    List orElse = list(LAMBDA, list(), s.get(3));
                    return compile1(list(cond, then, orElse));
                } else if (AND.equals(car)) {
                    // [and, a, b] => [if, a, b, #f]
                    assert sz == 3;
                    return compile1(list(IF, s.get(1), s.get(2), FALSE));
                } else if (OR.equals(car)) {
                    // [or, a, b] => [if, a, #t, b]
                    assert sz == 3;
                    return compile1(list(IF, s.get(1), TRUE, s.get(2)));
                }

                // Binding Forms
                else if (LET.equals(car)) {
                    return compileLet(s);
                } else if (LET_REC.equals(car)) {
                    return compileLetRec(s);
                }

                // Quote
                else if (QUOTE.equals(car)) {
                    assert sz == 2;
                    Object cdr = s.get(1);
                    // 只支持 ['quote', []] 表达 nil
                    assert cdr instanceof List;
                    assert ((List<?>) cdr).isEmpty();
                    return compile1(NIL);
                }

                // Application -- must be last
                else {
                    return compileApply(s);
                }
            }

            // Numerals
            else if (exp instanceof Integer) {
                // church-numeral
                return churchNumeral(((Integer) exp));
            }

            // Symbol & String
            else if (exp instanceof String) {
                String s = (String) exp;
                if (s.startsWith("\"")) {
                    assert s.endsWith("\"");
                    return compileStr(s.substring(1, s.length() - 1));
                } else {
                    return Sym.of(((String) exp));
                }
            }

            else {
                throw new IllegalStateException();
            }
        }

        static Expr compileLet(List lst) {
            // [let, [[v1, exp1], ... [vN, expN]], body] => [[lambda, [v1, ... vN], body], exp1, ... expN]
            int sz = lst.size();
            assert sz == 3;
            assert lst.get(1) instanceof List;
            List pairs = (List) lst.get(1);
            List<String> params = new ArrayList<>(pairs.size());
            List<Object> args = new ArrayList<>(pairs.size());
            for (Object pair : pairs) {
                assert pair instanceof List && ((List) pair).size() == 2;
                Object param = ((List) pair).get(0);
                assert param instanceof String;
                params.add(((String) param));
                args.add(((List) pair).get(1));
            }
            List<Object> let = list(list(LAMBDA, params, lst.get(2)));
            let.addAll(args);
            return compile1(let);
        }

        static Expr compileLetRec(List lst) {
            // ['letrec', [['f', 'lam']], 'body'] => ['let', [['f', ['Y', ['λ', ['f'], 'lam']]]], 'body']
            int sz = lst.size();
            assert sz == 3;
            assert lst.get(1) instanceof List;
            List pairs = (List) lst.get(1);
            assert pairs.size() == 1;
            Object pair0 = pairs.get(0);
            assert pair0 instanceof List && ((List) pair0).size() == 2;
            List pair = (List) pair0;
            Object f = pair.get(0);
            Object lam = pair.get(1);
            Object body = lst.get(2);
            List let = list(LET, list(list(f, list(Y, list(LAMBDA, list(f), lam)))), body);
            return compile1(let);
        }

        static Expr compileLambda(List lst) {
            // Currying
            // [lambda, [v1, ... vN], body] => [lambda, [v1], [lambda, [v2], ... [lambda, [vN], body]]]
            int sz = lst.size();
            assert sz == 3;
            Object params = lst.get(1);
            Object body = lst.get(2);

            assert params instanceof List;
            List paramLst = (List) params;
            int paramSz = paramLst.size();
            if (paramSz == 0) {
                return new Abs(Sym.of("_"), compile1(body));
            } else if (paramSz == 1) {
                Expr param = compile1(paramLst.get(0));
                assert param instanceof Sym;
                return new Abs((Sym) param, compile1(body));
            } else {
                Expr param = compile1(paramLst.get(0));
                assert param instanceof Sym;
                List subLam = list(LAMBDA, paramLst.subList(1, paramSz), body);
                return new Abs((Sym) param, compile1(subLam));
            }
        }

        static Expr compileApply(List lst) {
            int sz = lst.size();
            // Currying
            // [f, arg1, ... argN] => [... [[f, arg1], arg2], ... argN]
            // [a, b, c, c] => [[[a, b], c], d]
            if (sz == 1) {
                return new App(compile1(lst.get(0)), Sym.of(VOID));
            } else if (sz == 2) {
                return new App(compile1(lst.get(0)), compile1(lst.get(1)));
            } else {
                return new App(compile1(lst.subList(0, lst.size() - 1)), compile1(lst.get(lst.size() - 1)));
            }
        }

        static Expr compileStr(String s) {
            return compile1(cons(Arrays.asList(s.chars().boxed().toArray())));
        }

        static List<?> cons(List<?> els) {
            if (els.isEmpty()) {
                return list(QUOTE, list());
            } else {
                return list(CONS,  els.get(0), cons(els.subList(1, els.size())));
            }
        }

        static Object churchNumeralApplyN(String z, String f, int n) {
            assert n >= 0;
            if (n == 0) {
                return z;
            } else {
                return list(f, churchNumeralApplyN(z, f, n - 1));
            }
        }

        // 丘齐数就是将 f 应用到 z 的次数
        static Expr churchNumeral(int nat) {
            assert nat >= 0;
            String f = "f";
            String z = "z";
            if (nat == 0) {
                // [lambda, [f], [lambda, [z], z]]
                return compile1(list(LAMBDA, list(f), list(LAMBDA, list(z), z)));
            } else {
                // [lambda, [f], [lambda, [z] [$apply-n $n]]]
                return compile1(list(LAMBDA, list(f), list(LAMBDA, list(z), churchNumeralApplyN(z, f, nat))));
            }
        }

        static List<Object> list(Object ...its) {
            List<Object> lst = new ArrayList<>(its.length);
            Collections.addAll(lst, its);
            return lst;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /* --------------------  FFI ----------------------- */
    interface UnChurchification<T> {

        T unChurchify(F churchEncoded);

        interface F extends Function<F, F> {
            default int nat()        { return natify(this);  }
            default boolean bool()   { return boolify(this); }
            default String string()  { return stringify(this);  }
            default <T> Pair<T> list(UnChurchification<T> unChurch) { return listify(unChurch, this); }
        }

        CodeGen<F, Env<F>> compiler = new Compiler();

        class Compiler implements CodeGen<F, Env<F>> {
            @Override public F visit(Sym s, Env<F> env) { return env.lookup(s); }
            @Override public F visit(App s, Env<F> env) { return visit(s.abs, env).apply(visit(s.arg, env)); }
            @Override public F visit(Abs s, Env<F> env) {
                return arg -> {
                    Env<F> subEnv = new Env<>(env);
                    subEnv.put(s.param, arg);
                    return visit(s.body, subEnv);
                };
            }
        }


        F nil = f -> f;
        F succ = n -> f -> z -> f.apply(n.apply(f.apply(z))); // (λ (n) (λ (f) (λ (z) (f (n (f z))))))
        F zero = f -> null;
        F True = f -> f;
        F False = f -> f;

        static int natify(F churchNumeral) {
            F f = churchNumeral.apply(succ).apply(zero);
            int i = -1;
            while (f != null) {
                f = f.apply(nil);
                i++;
            }
            return i;
        }

        static boolean boolify(F churchBoolean) {
            F f = churchBoolean.apply(a -> True).apply(a -> False);
            if (f == True) {
                return true;
            } else if (f == False) {
                return false;
            } else {
                throw new IllegalStateException();
            }
        }

        static String stringify(F churchStr) {
            Pair<Integer> pair = listify(UnChurchification::natify, churchStr);
            byte[] bytes = new byte[pair.size()];
            int i = 0;
            while (pair.car != null) {
                bytes[i++] = pair.car.byteValue();
                pair = pair.cdr;
            }
            return new String(bytes, StandardCharsets.US_ASCII);
        }

        static <T> Pair<T> listify(UnChurchification<T> unChurch, F churchList) {
            class VF<T1> implements F {
                final T1 val;
                VF(T1 val) {
                    this.val = val;
                }
                @Override public F apply(F f) {
                    return nil.apply(f);
                }
            }
            F onCons = car -> cdr -> new VF<>(new Pair<>(unChurch.unChurchify(car), listify(unChurch, cdr)));
            F onNil = nil -> new VF<>(new Pair<>(null, null));
            //noinspection unchecked
            VF<Pair<T>> vf = ((VF<Pair<T>>) churchList.apply(onCons).apply(onNil));
            return vf.val;
        }

        class Pair<T> {
            final T car;
            final Pair<T> cdr;

            Pair(T car, Pair<T> cdr) {
                this.car = car;
                this.cdr = cdr;
            }

            int size() {
                return car == null ? 0 : 1 + cdr.size();
            }

            List<T> list() {
                List<T> lst = new ArrayList<>();
                Pair<T> cur = this;
                while (cur.car != null) {
                    lst.add(cur.car);
                    cur = cur.cdr;
                }
                return lst;
            }

            @SafeVarargs
            static <T> Pair<T> of(T ...els) {
                int sz = els.length;
                if (sz == 0) {
                    return new Pair<>(null, null);
                } else {
                    Pair<T> pair = new Pair<>(null, null);
                    for (int i = sz - 1; i >= 0; i--) {
                        pair = new Pair<>(els[i], pair);
                    }
                    return pair;
                }
            }

            @Override public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                return Objects.equals(car, ((Pair<?>) o).car) && Objects.equals(cdr, ((Pair<?>) o).cdr);
            }

            @Override public int hashCode() {
                return Objects.hash(car, cdr);
            }

            @Override public String toString() {
                if (car == null && cdr == null) {
                    return "nil";
                } else {
                    return "(" + car + " " + cdr + ")";
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    interface CodeGen<V, C> extends Visitor<V, C> {

        CodeGen<F, Env<F>> java = UnChurchification.compiler;

        CodeGen<Expr, Void> expr = new CodeGen<Expr, Void>() {
            @Override public Expr visit(Sym s, Void ctx) { return s; }
            @Override public Expr visit(App s, Void ctx) { return s; }
            @Override public Expr visit(Abs s, Void ctx) { return s; }
        };

        // natify : (+ n 1)(0)
        // boolity: (λ () #t)(λ () #f)
        CodeGen<String, Void> scheme = new CodeGen<String, Void>() {
            @Override public String visit(Sym s, Void v) {
                return s.name;
            }
            @Override public String visit(App s, Void v) {
                return "(" + visit(s.abs, v) + " " + visit(s.arg, v) + ")";
            }
            @Override public String visit(Abs s, Void v) {
                return "(" + LAMBDA + " (" + visit(s.param, v) + ") " + visit(s.body, v) + ")";
            }
        };

        CodeGen<String, Void> json = new CodeGen<String, Void>() {
            @Override public String visit(Sym s, Void v) {
                return "'" + s.name + "'";
            }
            @Override public String visit(App s, Void v) {
                return "[" + visit(s.abs, v) + ", " + visit(s.arg, v) + "]";
            }
            @Override public String visit(Abs s, Void v) {
                return "['" + LAMBDA + "', [" + visit(s.param, v) + "], " + visit(s.body, v) + "]";
            }
        };

        // natify : ((%s)(n => n + 1)(0))
        // boolify: ((%s)(_ => true)(_ => false))
        // (() => { let unchurchify = (churched) => churched(car => cdr => [car(n => n+1)(0), unchurchify(cdr)])(nil => null); return unchurchify })()(%s)
        CodeGen<String, Void> js = new CodeGen<String, Void>() {
            @Override public String visit(Sym s, Void v) {
                return s.name;
            }
            @Override public String visit(App s, Void v) {
                if (s.abs instanceof Sym) {
                    return visit(s.abs, v) + "(" + visit(s.arg, v) + ")";
                } else {
                    return "(" + visit(s.abs, v) + ")" + "(" + visit(s.arg, v) + ")";
                }
            }
            @Override public String visit(Abs s, Void v) {
                return "(" + visit(s.param, v) + " => " + visit(s.body, v) + ")";
            }
        };

        // natify : (lambda n: n + 1)(0)
        // boolify: (lambda _: true)(lambda _: false)
        CodeGen<String, Void> py = new CodeGen<String, Void>() {
            @Override public String visit(Sym s, Void v) {
                return s.name;
            }
            @Override public String visit(App s, Void v) {
                return "((" + visit(s.abs, v) + ")(" + visit(s.arg, v) + "))";
            }
            @Override public String visit(Abs s, Void v) {
                return "(lambda " + s.param.name + ": (" + visit(s.body, v) + "))";
            }
        };
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    interface Names {
        String VOID = "nothing";
        String LAMBDA = "λ";
        String QUOTE = "quote";

        // Boolean and conditionals
        String TRUE = "#t";
        String FALSE = "#f";
        String IF = "if";
        String AND = "and";
        String OR = "or";
        String NOT = "not";

        // Numerals
        String IS_ZERO = "zero?";
        String SUM = "+";
        String SUB = "-";
        String MUL = "*";
        String POW = "^";
        String MOD = "%";
        String DIV = "/";
        String EQ = "=";
        String NE = "!=";
        String LE = "<=";
        String GE = ">=";
        String LT = "<";
        String GT = ">";

        // Lists
        // String NIL = "nil";
        String CONS = "cons";
        String CAR = "car";
        String CDR = "cdr";
        String IS_PAIR = "pair?";
        String IS_NULL = "null?";

        // Bindings
        String LET = "let";
        String LET_REC = "letrec";
    }

    interface Primitives {
        // 这里不用定义成单参的函数, compile1 会做 curry 处理

        // (λ (_)
        //  ((λ (f) (f f))
        //   (λ (f) (f f))))
        String S_ERROR = "['λ', ['_'], [['λ', ['f'], ['f', 'f']], ['λ', ['f'], ['f', 'f']]]]";

        // https://www.slideshare.net/yinwang0/reinventing-the-ycombinator
        String S_Y = "[['λ', ['y'], ['λ', ['F'], ['F', ['λ', ['x'], [[['y', 'y'], 'F'], 'x']]]]],\n" +
                     " ['λ', ['y'], ['λ', ['F'], ['F', ['λ', ['x'], [[['y', 'y'], 'F'], 'x']]]]]]";

        // (λ (void) void)
        String S_VOID = "['λ', ['" + VOID + "'], '" + VOID + "']";

        // (λ (on_cons) (λ (on_nil) (on_nil ,S_VOID)) )
        // (λ (on_cons on_nil) (on_nil ,S_VOID))
        String S_NIL = "['λ', ['on_cons', 'on_nil'], ['on_nil', " + S_VOID + "]]";

        // (λ (t) (λ (f) (t ,S_VOID)))
        String S_TRUE = "['λ', ['t', 'f'], ['t', '" + VOID + "']]";
        // (λ (t) (λ (f) (f ,S_VOID)))
        String S_FALSE = "['λ',['t', 'f'], ['f', '" + VOID + "']]";

        String S_THUNK_TRUE = "['λ', ['_'], " + S_TRUE + "]";;
        String S_THUNK_FALSE = "['λ', ['_'], " + S_FALSE + "]";

        String S_NOT = "['λ', ['b'], ['b', " + S_THUNK_FALSE + ", " + S_THUNK_TRUE + "]]";

        // (λ (n) ((n (λ (_) ,S_FALSE)) ,S_TRUE))
        String S_IS_ZERO = "['λ', ['n'], [['n', " + S_THUNK_FALSE + "], " + S_TRUE + "]]";


        // (λ (n) (λ (f) (λ (z) (f (n (f z))))))
        // (λ (n f z) (f (n f z)))
        String S_SUCC = "['λ', ['n', 'f', 'z'], ['f', ['n', 'f', 'z']]]";

        // (λ (n) (λ (m) (λ (f) (λ (z) ((m f) ((n f) z))))))
        // (λ (n m)  (λ (f z) (m f (n f z))))
        // (λ (n m f z)  (m f (n f z)))
        String S_SUM = "['λ', ['n', 'm', 'f', 'z'], ['m', 'f', ['n', 'f', 'z']]]";

        // (λ (n) (λ (m) (λ (f) (λ (z) ((m (n f)) z)))))
        // (λ (n m)  (λ (f z) (m (n f) z)))
        // (λ (n m f z) (m (n f) z))
        String S_MUL = "['λ', ['n', 'm', 'f', 'z'], ['m', ['n', 'f'], 'z']]";

        String S_ONE = "['λ', ['f', 'z'], ['f', 'z']]";
        String S_POW = "['λ', ['m', 'n'], [['n', [" + S_MUL + ", 'm']], " + S_ONE + "]]";

        // (Y (λ (mod)
        //     (λ (m n)
        //       (if (<= n m)
        //           (mod (- m n) n)
        //           m))))
        String S_MOD = "[" + S_Y + ", ['λ', ['mod'],\n" +
                "  ['λ', ['m', 'n'],\n" +
                "    ['if', ['=', 'n', 0], \n" +
                "           " + S_ERROR + ", \n" +
                "           ['if', ['<=', 'n', 'm'],\n" +
                "                  ['mod', ['-', 'm', 'n'], 'n'],\n" +
                "                  'm']]]]]";

        String S_DIV = "[" + S_Y + ", ['λ', ['div'],\n" +
                "  ['λ', ['m', 'n'],\n" +
                "    ['if', ['=', 'n', 0], \n" +
                "           " + S_ERROR + ", \n" +
                "           ['if', ['<=', 'n', 'm'],\n" +
                "                  ['+', 1, ['div', ['-', 'm', 'n'], 'n']],\n" +
                "                  0]]]]]";

        // (λ (n) (λ (f) (λ (z) (((n (λ (g) (λ (h) (h (g f))))) (λ (u) z)) (λ (u) u)))))
        // (λ (n f z) (n   (λ (g h) (h (g f)))    (λ (u) z)    (λ (u) u)  ))
        String S_PRED = "['λ', ['n', 'f', 'z'],  ['n',   ['λ', ['g', 'h'], ['h', ['g', 'f']]],   ['λ', ['u'], 'z'],    ['λ', ['u'], 'u']  ]]";

        // 注意: 这路程针对自然数的减法变种，称作饱和减法 （Monus，由 minus 修改而来）
        // 由于没有负的自然数，如果被减数比减数小，我们就将结果取零。
        // (λ (n) (λ (m) ((m ,S_PRED) n)))
        // (λ (n m) (m ,S_PRED n))
        String S_SUB = "['λ', ['n', 'm'], ['m', " + S_PRED + ", 'n']]";

        // (λ (x y) (and (zero? (- x y)) (zero? (- y x))))
        String S_EQ = "['λ', ['x', 'y'], ['" + AND + "',   ['" + IS_ZERO + "', ['-', 'x', 'y']],   ['" + IS_ZERO + "', ['-', 'y', 'x']]]]";

        String S_NE = "['λ', ['x', 'y'], [" + S_NOT + ", [" + S_EQ + ", 'x', 'y']]]";

        String S_LE = "['λ', ['m', 'n'], ['zero?', ['-', 'm', 'n']]]";
        String S_GE = "['λ', ['m', 'n'], ['zero?', ['-', 'n', 'm']]]";

        String S_LT = "['λ', ['m', 'n'], ['and', [" + S_LE + ", 'm', 'n'], [" + S_NE + ", 'm', 'n']]]";
        String S_GT = "['λ', ['m', 'n'], ['and', [" + S_GE + ", 'm', 'n'], [" + S_NE + ", 'm', 'n']]]";

        // (λ (car) (λ (cdr) (λ (on_cons) (λ (on_nil) ((on_cons car) cdr)))))
        // (λ (car cdr on_cons on_nil) (on_cons car cdr))
        String S_CONS = "['λ', ['car', 'cdr', 'on_cons', 'on_nil'], ['on_cons', 'car', 'cdr']]";

        // (λ (list) ((list (λ (car) (λ (cdr) car))) ,S_ERROR))
        // (λ (list) (list (λ (car cdr) car) ,S_ERROR))
        String S_CAR = "['λ', ['list'], ['list', ['λ', ['car', 'cdr'], 'car'], " + S_ERROR + "]]";

        // (λ (list) ((list (λ (car) (λ (cdr) cdr))) ,ERROR))
        // (λ (list) (list (λ (car cdr) cdr) ,S_ERROR))
        String S_CDR = "['λ', ['list'], ['list', ['λ', ['car', 'cdr'], 'cdr'], " + S_ERROR + "]]";

        // (λ (list) ((list (λ (_) (λ (_) ,S_TRUE))) (λ (_) ,S_FALSE)))
        // (λ (list) (list (λ (_1 _2) ,S_TRUE) (λ (_) ,S_FALSE)))
        String S_IS_PAIR = "['λ', ['list'], ['list', ['λ', ['_'], " + S_THUNK_TRUE + "], " + S_THUNK_FALSE + "]]";

        // (λ (list) ((list (λ (_) (λ (_) ,S_FALSE))) (λ (_) ,S_TRUE)))
        // (λ (list) (list (λ (_1 _2) ,S_FALSE) (λ (_) ,S_TRUE)))
        String S_IS_NULL = "['λ', ['list'], ['list', ['λ', ['_'], " + S_THUNK_FALSE + "], " + S_THUNK_TRUE + "]]";

    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /* ----------------------- Value & Interpreter ---------------------- */
    // 演示解释器写法, 没什么用
    // 这个解释器的环境其实没什么卵用, 编译器已经把自由变量干掉了, 也不需要环境, 仅仅在做代换处理

    /*
    interface Val { }
    class Closure implements Val {
        final Abs abs;
        final Env<Val> env;

        Closure(Abs abs, Env<Val> env) {
            this.abs = abs;
            this.env = env;
        }

        @Override public String toString() {
            return CodeGen.scheme(abs);
        }
    }

    class Interp implements Visitor<Val, Env<Val>> {
        @Override public Val visit(Sym s, Env<Val> env) { return env.lookup(s);         }
        @Override public Val visit(App s, Env<Val> env) { return apply(s, env);         }
        @Override public Val visit(Abs s, Env<Val> env) { return new Closure(s, env);   }

        // eval $ apply
        public Val eval(Expr s, Env<Val> env) { return visit(s, env); }

        Val apply(App app, Env<Val> env) {
            Val val = eval(app.abs, env);
            if (val instanceof Closure) {
                Closure closure = ((Closure) val);
                Env<Val> subEnv = new Env<>(closure.env);
                Val arg = eval(app.arg, env);
                subEnv.put(closure.abs.param, arg);
                return eval(closure.abs.body, subEnv);
            } else {
                return val;
            }
        }
    }
     */
}
