package xiao;

import java.util.function.Function;

/**
 * @author chuxiaofeng
 */
public interface Fold {

    interface F<A, R> {
        R call(A a);
    }

    interface F2<A, B, R> {
        R call(A a, B b);
    }

    class Pair<A> {
        final A car;
        final Pair<A> cdr;
        public Pair(A car, Pair<A> cdr) {
            this.car = car;
            this.cdr = cdr;
        }
        @Override public String toString() {
            return "(" + car.toString() + ", " + (cdr == null ? "nil" : cdr.toString()) + ")";
        }
    }

    static <A> A car(Pair<A> l) {
        if (l == null) {
            throw new IllegalStateException();
        } else {
            return l.car;
        }
    }

    static <A> Pair<A> cdr(Pair<A> l) {
        if (l == null) {
            throw new IllegalStateException();
        } else {
            return l.cdr;
        }
    }

    static <A> Pair<A> cons(A x, Pair<A> xs) {
        return new Pair<>(x, xs);
    }

    // rightReduce
    static <A, R> R foldr(Pair<A> l, F2<A, R, R> f, R z) {
        if (l == null) {
            return z;
        } else {
            return f.call(car(l), foldr(cdr(l), f, z));
        }
    }

    // leftReduce
    static <A, R> R foldl(Pair<A> l, F2<A, R, R> f, R z) {
        if (l == null) {
            return z;
        } else {
            return foldl(cdr(l), f, f.call(car(l), z));
        }
    }

    static <A, R> Pair<R> map(Pair<A> l, F<A, R> f) {
        return foldr(l, (a, b) -> cons(f.call(a), b), null);
    }

    static <A> Pair<A> reverse(Pair<A> l) {
        return foldl(l, Fold::cons, null);
    }

    static void main(String[] args) {
        Pair<Integer> l = cons(1, cons(2, cons(3, null)));

        System.out.println(foldl(l, Integer::sum, 0));
        System.out.println(foldr(l, Integer::sum, 0));

        System.out.println("+1 $ " + l + " " + map(l, a -> a + 1));
        System.out.println(l + " reverse " + reverse(l));
    }
}
