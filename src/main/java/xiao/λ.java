package xiao;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.lang.Character.isDigit;
import static java.lang.Character.isWhitespace;
import static java.util.stream.Collectors.toList;
import static xiao.λ.Compiler.compile1;
import static xiao.λ.Expr.*;
import static xiao.λ.Names.*;
import static xiao.λ.Parser.*;
import static xiao.λ.Parser.Node.*;
import static xiao.λ.Primitives.*;
import static xiao.λ.UnChurchification.F;

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

    // close 掉 free variable 的 bootstrap 环境
    static Env<Expr> bootEnv() {
        return bootEnv(Compiler.expander);
    }

    // 用 visitor 构建一个 bootstrap 环境
    static <T> Env<T> bootEnv(Visitor<T, Env<T>> vis) {
        Env<T> env = new Env<>(null);
        primitives().forEach((n, s) -> env.put(symOf(n), vis.visit(compile1(parse(s)), env)));
        return env;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // 试验另一种方式: 不用环境, 把 runtime 放到顶层的 let 里头, 理论可行, 编译到 js 一些 sym 名称不是合法的变量
    // 而环境替换的方式, 会全部替换掉, 那些 sym 定义的名字都会消失
    /*
    static <Target, Ctx> Target compileWithGlobalLet(String code, CodeGen<Target, Ctx> gen) {
        List<Node> pairs = new ArrayList<>();
        primitives().forEach((n, s) -> pairs.add(tupleOf(nameOf(n), parse(s))));
        Tuple globalLet = tupleOf(Compiler.let, tupleOf(pairs), parse(code));
        return gen.visit(Compiler.compile(globalLet, null), null);
    }
    */

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /* ----------------------- AST ------------------------ */
    interface Expr {
        class Sym implements Expr {
            final String name;
            private Sym(String name) { this.name = name; }
            @Override public String toString() { return name; }
        }
        class App implements Expr {
            final Expr abs;
            final Expr arg;
            App(Expr abs, Expr arg) {
                this.abs = abs;
                this.arg = arg;
            }
            @Override public String toString() { return "(" + abs + " " + arg + ")"; }
        }
        class Abs implements Expr {
            final Sym param;
            final Expr body;
            Abs(Sym param, Expr body) {
                this.param = param;
                this.body = body;
            }
            @Override public String toString() { return "(" + LAMBDA + " (" + param + ") " + body + ")"; }
        }

        Map<String, Sym> symCache = new HashMap<>();
        static Sym symOf(String name) { return symCache.computeIfAbsent(name, t -> new Sym(name)); }
    }

    interface Visitor<V, C> {
        V visit(Sym s, C ctx);
        V visit(App s, C ctx);
        V visit(Abs s, C ctx);
        default V visit(Expr s, C ctx) {
            if (s instanceof Sym) return visit(((Sym) s), ctx);
            if (s instanceof App) return visit(((App) s), ctx);
            if (s instanceof Abs) return visit(((Abs) s), ctx);
            else                  throw new UnsupportedOperationException();
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

        interface Node {
            class Delimiter implements Node {
                final char shape;
                Delimiter(char shape) { this.shape = shape; }
                @Override public String toString() { return Character.toString(shape); }
            }
            class Name implements Node {
                final String id;
                Name(String id) { this.id = id; }
                @Override public String toString() { return id; }
            }
            class Tuple implements Node {
                final List<Node> els;
                Tuple(List<Node> els) {
                    this.els = els;
                }
                @Override public String toString() {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < els.size(); i++) {
                        sb.append(els.get(i).toString());
                        if (i != els.size() - 1) {
                            sb.append(" ");
                        }
                    }
                    return TUPLE_BEGIN + sb.toString() + TUPLE_END;
                }
            }
            class Str implements Node {
                final String value;
                Str(String value) { this.value = value; }
                @Override public String toString() { return "\"" + value + "\""; }
            }
            class Int implements Node {
                @Override public String toString() { return Integer.toString(value); }
                final int value;
                Int(int val) { this.value = val; }
                Int(String content) { this.value = Integer.parseInt(content); }
            }

            static Tuple tupleOf(Node ...its) {
                List<Node> lst = new ArrayList<>(its.length);
                Collections.addAll(lst, its);
                return new Tuple(lst);
            }

            static Tuple tupleOf(List<Node> lst) { return new Tuple(lst); }
            static Name nameOf(String s) { return new Name(s); }
            static Str strOf(String s) { return new Str(s); }
            static Int intOf(int i) { return new Int(i); }
        }

        public static Node parse(String s) {
            List<Node> nodes = new Parser(s).parse();
            assert nodes.size() == 1;
            return nodes.get(0);
        }

        final static String LINE_COMMENT = ";";
        final static char TUPLE_BEGIN = '(';
        final static char TUPLE_END = ')';

        final String input;
        int offset = 0;

        public Parser(String input) {
            this.input = input;
        }

        public List<Node> parse() {
            List<Node> elements = new ArrayList<>();
            Node s = nextSexp();
            while (s != null) {
                elements.add(s);
                s = nextSexp();
            }
            return elements;
        }

        Node nextSexp() { return nextNode(0); }

        /*@Nullable*/ Node nextNode(int depth) {
            Node begin = nextToken();
            if (begin == null) {
                return null;
            }

            if (depth == 0 && isClose(begin)) {
                throw new RuntimeException("不匹配: " + begin);
            } else if (isOpen(begin)) {
                List<Node> elements = new ArrayList<>();
                Node iter = nextNode(depth + 1);
                while (!matchDelimiter(begin, iter)) {
                    if (iter == null) {
                        throw new RuntimeException("未闭合: " + begin);
                    } else if (isClose(iter)) {
                        throw new RuntimeException("不匹配: " + iter);
                    } else {
                        elements.add(iter);
                        iter = nextNode(depth + 1);
                    }
                }
                return new Tuple(elements);
            } else {
                return begin;
            }
        }

        /*@Nullable*/ Node nextToken() {
            skipComment();
            if (offset >= input.length()) {
                return null;
            }

            char cur = input.charAt(offset);
            if (isDelimiter(cur)) {
                offset++;
                return new Delimiter(cur);
            }

            if (input.charAt(offset) == '"' && (offset == 0 || input.charAt(offset - 1) != '\\')) {
                int start = offset;
                offset++; // skip "
                while (offset < input.length() && !(input.charAt(offset) == '"' && input.charAt(offset - 1) != '\\')) {
                    if (input.charAt(offset) == '\n') {
                        throw new RuntimeException("字符串不能换行");
                    }
                    offset++;
                }
                if (offset >= input.length()) {
                    throw new RuntimeException("未闭合字符串");
                }
                offset++; // skip "
                int end = offset;
                String content = input.substring(start + 1, end - 1);
                return new Str(content);
            }

            int start = offset;
            if (isDigit(input.charAt(start))) {
                while (offset < input.length() && !isWhitespace(cur) && !isDelimiter(cur)) {
                    if (++offset < input.length()) {
                        cur = input.charAt(offset);
                    }
                }
                return new Int(input.substring(start, offset));
            }

            while (offset < input.length() && !isWhitespace(cur) && !isDelimiter(cur)) {
                if (++offset < input.length()) {
                    cur = input.charAt(offset);
                }
            }
            return new Name(input.substring(start, offset));
        }

        void skipComment() {
            boolean seenComment = true;
            while (seenComment) {
                seenComment = false;
                while (offset < input.length() && isWhitespace(input.charAt(offset))) {
                    offset++;
                }
                if (offset + LINE_COMMENT.length() <= input.length() && input.startsWith(LINE_COMMENT, offset)) {
                    while (offset < input.length() && input.charAt(offset) != '\n') {
                        offset++;
                    }
                    if (offset < input.length()) {
                        offset++;
                    }
                    seenComment = true;
                }
            }
        }

        boolean isDelimiter(char c) { return c == TUPLE_BEGIN || c == TUPLE_END; }
        boolean isOpen(Node c) { return c instanceof Delimiter && ((Delimiter) c).shape == TUPLE_BEGIN; }
        boolean isClose(Node c) { return c instanceof Delimiter && ((Delimiter) c).shape == TUPLE_END; }
        boolean matchDelimiter(Node open, Node close) { return isOpen(open) && isClose(close); }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /* --------------------  Compiler ----------------------- */
    class Compiler {
        final static Visitor<Expr, Env<Expr>> expander = new Expander();

        // expander 负责把 close 掉 free var, 保证生成结果 expr 都是 closed term
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

        static Expr compile(Node node, Env<Expr> env) {
            return expander.visit(compile1(node), env);
        }

        static boolean is(Node n, String s) {
            return (n instanceof Name) && ((Name) n).id.equals(s);
        }

        // 注意: 不能直接替换代码, 一个case (λ (+) (+ 0 0))
        static Expr compile1(Node n) {
            if (n instanceof Tuple) {
                List<Node> ns = ((Tuple) n).els;
                int sz = ns.size();
                assert sz != 0;

                Node car = ns.get(0);

                // Lambdas
                if (is(car, LAMBDA)) {
                    return compileLambda(ns);
                }

                // Conditionals
                // and or 需要处理短路, 不能定义成 primitive
                if (is(car, IF)) {
                    // (if cond then orElse) ~> (cond (lambda () then) (lambda () orElse))
                    assert sz == 4;
                    Node cond = ns.get(1);
                    Tuple then = tupleOf(λ, tupleOf(), ns.get(2));
                    Tuple orElse = tupleOf(λ, tupleOf(), ns.get(3));
                    return compile1(tupleOf(cond, then, orElse));
                }
                if (is(car, AND)) {
                    // (and a b) ~> (if a b #f)
                    assert sz == 3;
                    return compile1(tupleOf(iff, ns.get(1), ns.get(2), False));
                }
                if (is(car, OR)) {
                    // (or a b) ~> (if a #t b)
                    assert sz == 3;
                    return compile1(tupleOf(iff, ns.get(1), True, ns.get(2)));
                }

                // Binding Forms
                if (is(car, LET)) {
                    return compileLet(ns);
                }
                if (is(car, LET_REC)) {
                    return compileLetRec(ns);
                }

                // Quote
                if (is(car, QUOTE)) {
                    assert sz == 2;
                    Node cdr = ns.get(1);
                    // 只支持 (quote ()) 表达 nil
                    assert cdr instanceof Tuple;
                    assert ((Tuple) cdr).els.isEmpty();
                    return compile1(NIL);
                }

                // Application -- must be last
                return compileApply(ns);
            }

            // Numerals
            if (n instanceof Int) {
                return churchNumeral((Int) n);
            }

            // Symbol & String
            if (n instanceof Str) {
                return compileStr((Str) n);
            }

            if (n instanceof Name) {
                return symOf(((Name) n).id);
            }

            throw new IllegalStateException();
        }

        // (let ((v1 exp1) ... (vN expN)) body) ~> ((lambda (v1 ... vN) body) exp1 ... expN)
        static Expr compileLet(List<Node> ns) {
            int sz = ns.size();
            assert sz == 3;
            assert ns.get(1) instanceof Tuple;

            List<Node> pairs = ((Tuple) ns.get(1)).els;
            List<Node> params = new ArrayList<>(pairs.size());
            List<Node> applyArgs = new ArrayList<>(pairs.size() + 1);

            Node body = ns.get(2);
            Tuple apply = tupleOf(λ, tupleOf(params), body);
            applyArgs.add(apply);

            for (Node it : pairs) {
                assert it instanceof Tuple;
                List<Node> pair = ((Tuple) it).els;
                assert pair.size() == 2;

                Node param = pair.get(0);
                assert param instanceof Name;
                params.add(param);

                Node arg = pair.get(1);
                applyArgs.add(arg);
            }
            // let 声明的变量之间不能相互依赖
            return compile1(tupleOf(applyArgs));
        }

        // (letrec ((f lam)) body) ~> (let ((f (Y (λ (f) lam)))) body)
        static Expr compileLetRec(List<Node> ns) {
            int sz = ns.size();
            assert sz == 3;
            assert ns.get(1) instanceof Tuple;
            List<Node> pairs = ((Tuple) ns.get(1)).els;
            assert pairs.size() == 1;

            Node pair0 = pairs.get(0);
            assert pair0 instanceof Tuple;
            List<Node> pair = ((Tuple) pair0).els;
            assert pair.size() == 2;

            Node f = pair.get(0);
            Node lam = pair.get(1);
            Node body = ns.get(2);
            Tuple letrec = tupleOf(let, tupleOf(tupleOf(f, tupleOf(Y, tupleOf(λ, tupleOf(f), lam)))), body);
            return compile1(letrec);
        }

        // Currying
        // (λ (v1 ... vN) body) ~> (λ (v1) (λ (v2) ... (λ (vN) body)))
        static Expr compileLambda(List<Node> ns) {
            int sz = ns.size();
            assert sz == 3;
            Node params = ns.get(1);
            Node body = ns.get(2);

            assert params instanceof Tuple;
            List<Node> paramLst = ((Tuple) params).els;
            int paramSz = paramLst.size();
            if (paramSz == 0) {
                return new Abs(symOf("_"), compile1(body));
            } else if (paramSz == 1) {
                Expr param = compile1(paramLst.get(0));
                assert param instanceof Sym;
                return new Abs((Sym) param, compile1(body));
            } else {
                Expr param = compile1(paramLst.get(0));
                assert param instanceof Sym;
                Tuple subLam = tupleOf(λ, tupleOf(paramLst.subList(1, paramSz)), body);
                return new Abs((Sym) param, compile1(subLam));
            }
        }

        // Currying
        // (f arg1 ... argN) ~> (... ((f arg1) arg2) ... argN)
        // (a b c c) ~> (((a b) c) d)
        static Expr compileApply(List<Node> ns) {
            int sz = ns.size();
            if (sz == 1) {
                return new App(compile1(ns.get(0)), symOf(VOID));
            } else if (sz == 2) {
                return new App(compile1(ns.get(0)), compile1(ns.get(1)));
            } else {
                return new App(compile1(tupleOf(ns.subList(0, ns.size() - 1))), compile1(ns.get(ns.size() - 1)));
            }
        }

        // 丘齐数就是将 f 应用到 z 的次数
        // 0:  (λ (f) (λ (z) z))
        // (λ (f) (λ (z) ($apply-n $n)))  -> (λ (f z) ($apply-n $n))
        static Expr churchNumeral(Int nat) {
            assert nat.value >= 0;
            Name f = nameOf("f");
            Name z = nameOf("z");
            Node applyN = z;
            for (int i = 0; i < nat.value; i++) {
                applyN = tupleOf(f, applyN);
            }
            return compile1(tupleOf(λ, tupleOf(f, z), applyN));
        }

        static Expr compileStr(Str s) {
            /*return compile1(new StringBuilder(s.value).reverse().chars().boxed()
                    .map(Node::intOf).reduce(
                            tupleOf(quote, tupleOf()),
                            (acc, i) -> tupleOf(cons, i, acc),
                            (a, b) -> b));*/
            return compile1(cons(s.value.chars().boxed().map(Node::intOf).collect(toList())));
        }

        static Tuple cons(List<Node> els) {
            if (els.isEmpty()) {
                return tupleOf(quote, tupleOf());
            } else {
                return tupleOf(cons,  els.get(0), cons(els.subList(1, els.size())));
            }
        }

        final static Node Y = parse(S_Y);
        final static Node NIL = parse(S_NIL);

        final static Name λ = nameOf(LAMBDA);
        final static Name let = nameOf(LET);
        final static Name cons = nameOf(CONS);
        final static Name quote = nameOf(QUOTE);
        final static Name iff = nameOf(IF);
        final static Name True = nameOf(TRUE);
//        final static Name False = nameOf(FALSE);
        final static Node False = parse(S_FALSE);
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
    /* --------------------  CodeGen ----------------------- */
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
            @Override public String visit(Sym s, Void v) { return s.name; }
            @Override public String visit(App s, Void v) { return "(" + visit(s.abs, v) + " " + visit(s.arg, v) + ")"; }
            @Override public String visit(Abs s, Void v) { return "(" + LAMBDA + " (" + visit(s.param, v) + ") " + visit(s.body, v) + ")"; }
        };

        CodeGen<String, Void> json = new CodeGen<String, Void>() {
            @Override public String visit(Sym s, Void v) { return "'" + s.name + "'"; }
            @Override public String visit(App s, Void v) { return "[" + visit(s.abs, v) + ", " + visit(s.arg, v) + "]"; }
            @Override public String visit(Abs s, Void v) { return "['" + LAMBDA + "', [" + visit(s.param, v) + "], " + visit(s.body, v) + "]"; }
        };

        // natify : ((%s)(n => n + 1)(0))
        // boolify: ((%s)(_ => true)(_ => false))
        // (() => { let unchurchify = (churched) => churched(car => cdr => [car(n => n+1)(0), unchurchify(cdr)])(nil => null); return unchurchify })()(%s)
        CodeGen<String, Void> js = new CodeGen<String, Void>() {
            @Override public String visit(Sym s, Void v) { return s.name; }
            @Override public String visit(App s, Void v) {
                if (s.abs instanceof Sym) {
                    return visit(s.abs, v) + "(" + visit(s.arg, v) + ")";
                } else {
                    return "(" + visit(s.abs, v) + ")" + "(" + visit(s.arg, v) + ")";
                }
            }
            @Override public String visit(Abs s, Void v) { return "(" + visit(s.param, v) + " => " + visit(s.body, v) + ")"; }
        };

        // natify : (lambda n: n + 1)(0)
        // boolify: (lambda _: true)(lambda _: false)
        CodeGen<String, Void> py = new CodeGen<String, Void>() {
            @Override public String visit(Sym s, Void v) { return s.name; }
            @Override public String visit(App s, Void v) { return "((" + visit(s.abs, v) + ")(" + visit(s.arg, v) + "))"; }
            @Override public String visit(Abs s, Void v) { return "(lambda " + s.param.name + ": (" + visit(s.body, v) + "))"; }
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

    static Map<String, String> primitives() {
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

        return primitives;
    }

    interface Primitives {
        // 这里不用定义成单参的函数, compile1 会做 curry 处理

        String S_ERROR = "(λ (_) " +
                            "((λ (f) (f f)) " +
                             "(λ (f) (f f))))";

        // https://www.slideshare.net/yinwang0/reinventing-the-ycombinator
        String S_Y = "((λ (y) (λ (F) (F (λ (x) (((y y) F) x))))) " +
                      "(λ (y) (λ (F) (F (λ (x) (((y y) F) x))))))";

        // (λ (void) void)
        String S_VOID = "(λ (" + VOID + ") " + VOID + ")";

        // (λ (on_cons) (λ (on_nil) (on_nil ,S_VOID)) )
        // (λ (on_cons on_nil) (on_nil ,S_VOID))
        String S_NIL = "(λ (on_cons on_nil) (on_nil " + S_VOID + "))";

        // (λ (t) (λ (f) (t ,S_VOID)))
        String S_TRUE = "(λ (t f) (t " + S_VOID + "))";

        // (λ (t) (λ (f) (f ,S_VOID)))
        String S_FALSE = "(λ (t f) (f " + S_VOID + "))";

        String S_THUNK_TRUE = "(λ (_) " + S_TRUE + ")";
        String S_THUNK_FALSE = "(λ (_) " + S_FALSE + ")";


        String S_NOT = "(λ (b) (b " + S_THUNK_FALSE + " " + S_THUNK_TRUE + "))";

        // (λ (n) ((n (λ (_) ,S_FALSE)) ,S_TRUE))
        String S_IS_ZERO = "(λ (n) ((n " + S_THUNK_FALSE + ") " + S_TRUE + "))";


        // (λ (n) (λ (f) (λ (z) (f (n (f z))))))
        // (λ (n f z) (f (n f z)))
        String S_SUCC = "(λ (n f z) (f (n f z)))";

        // (λ (n) (λ (m) (λ (f) (λ (z) ((m f) ((n f) z))))))
        // (λ (n m)  (λ (f z) (m f (n f z))))
        // (λ (n m f z)  (m f (n f z)))
        String S_SUM = "(λ (n m f z) (m f (n f z)))";

        // (λ (n) (λ (m) (λ (f) (λ (z) ((m (n f)) z)))))
        // (λ (n m)  (λ (f z) (m (n f) z)))
        // (λ (n m f z) (m (n f) z))
        String S_MUL = "(λ (n m f z) (m (n f) z))";

        String S_ONE = "(λ (f z) (f z))";

        String S_POW = "(λ (m n) ((n (" + S_MUL + " m)) " +  S_ONE + "))";

        // (λ (n) (λ (f) (λ (z) (((n (λ (g) (λ (h) (h (g f))))) (λ (u) z)) (λ (u) u)))))
        // (λ (n f z) (n   (λ (g h) (h (g f)))    (λ (u) z)    (λ (u) u)  ))
        String S_PRED = "(λ (n f z) (n (λ (g h) (h (g f))) (λ (u) z) (λ (u) u)))";

        // 注意: 这路程针对自然数的减法变种，称作饱和减法 （Monus，由 minus 修改而来）
        // 由于没有负的自然数，如果被减数比减数小，我们就将结果取零。
        // (λ (n) (λ (m) ((m ,S_PRED) n)))
        // (λ (n m) (m ,S_PRED n))
        String S_SUB = "(λ (n m) (m " + S_PRED + " n))";

        // (λ (x y) (and (zero? (- x y)) (zero? (- y x))))
        String S_EQ = "(λ (x y) (" + AND + " (" + S_IS_ZERO + " (" + S_SUB + " x y)) (" + S_IS_ZERO + " (" + S_SUB + " y x))))";

        String S_NE = "(λ (x y) (" + S_NOT + " (" + S_EQ + " x y)))";

        String S_LE = "(λ (m n) (" + S_IS_ZERO + " (" + S_SUB + " m n)))";
        String S_GE = "(λ (m n) (" + S_IS_ZERO + " (" + S_SUB + " n m)))";

        String S_LT = "(λ (m n) (" + AND + " (" + S_LE + " m n) (" + S_NE + " m n)))";
        String S_GT = "(λ (m n) (" + AND + " (" + S_GE + " m n) (" + S_NE + " m n)))";

        // (Y (λ (mod)
        //     (λ (m n)
        //       (if (<= n m)
        //           (mod (- m n) n)
        //           m))))
        String S_MOD = "(" + S_Y + " (λ (mod) (λ (m n) (if (" + S_EQ + " n 0) " + S_ERROR + " (if (" + S_LE + " n m) (mod (" + S_SUB + " m n) n) m)))))";
        String S_DIV = "(" + S_Y + " (λ (div) (λ (m n) (if (" + S_EQ + " n 0) " + S_ERROR + " (if (" + S_LE + " n m) (" + S_SUM + " 1 (div (" + S_SUB + " m n) n)) 0)))))";


        // (λ (car) (λ (cdr) (λ (on_cons) (λ (on_nil) ((on_cons car) cdr)))))
        // (λ (car cdr on_cons on_nil) (on_cons car cdr))
        String S_CONS = "(λ (car cdr on_cons on_nil) (on_cons car cdr))";

        // (λ (list) ((list (λ (car) (λ (cdr) car))) ,S_ERROR))
        // (λ (list) (list (λ (car cdr) car) ,S_ERROR))
        String S_CAR = "(λ (list) (list (λ (car cdr) car) " + S_ERROR + "))";

        // (λ (list) ((list (λ (car) (λ (cdr) cdr))) ,ERROR))
        // (λ (list) (list (λ (car cdr) cdr) ,S_ERROR))
        String S_CDR = "(λ (list) (list (λ (car cdr) cdr) " + S_ERROR + "))";

        // (λ (list) ((list (λ (_) (λ (_) ,S_TRUE))) (λ (_) ,S_FALSE)))
        // (λ (list) (list (λ (_1 _2) ,S_TRUE) (λ (_) ,S_FALSE)))
        String S_IS_PAIR = "(λ (list) (list (λ (_) " + S_THUNK_TRUE + ") " + S_THUNK_FALSE + "))";

        // (λ (list) ((list (λ (_) (λ (_) ,S_FALSE))) (λ (_) ,S_TRUE)))
        // (λ (list) (list (λ (_1 _2) ,S_FALSE) (λ (_) ,S_TRUE)))
        String S_IS_NULL = "(λ (list) (list (λ (_) " + S_THUNK_FALSE + ") " + S_THUNK_TRUE + "))";
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

    /* ----------------------- Parser ---------------------- */
    // 旧的 Parser, 使用 json array 当做 S表达式
    /*
    class JArrayParser {
        final static javax.script.ScriptEngine engine
                = new javax.script.ScriptEngineManager().getEngineByName("javascript");

        public static Node parse(String s) {
            return convert(decode(s));
        }

        // String ->  Map | List | String | Integer
        static Object decode(String s) {
            try {
                return engine.eval("Java.asJSONCompatible(" + s + ")");
            } catch (javax.script.ScriptException e) {
                throw new RuntimeException(e);
            }
        }

        static Node convert(Object n) {
            if (n instanceof List) {
                return new Tuple(((List<?>) n).stream().map(JArrayParser::convert).collect(toList()));
            } else if (n instanceof Integer) {
                return new Int(((Integer) n));
            } else if (n instanceof String) {
                String str = (String) n;
                if (str.startsWith("\"")) {
                    assert str.endsWith("\"");
                    return new Str(str.substring(1, str.length() - 1));
                } else {
                    return new Name(((String) n));
                }
            } else {
                throw new IllegalStateException();
            }
        }
    }
    */
}
