package io.nucleo.net.util;

public class Tuple3<A, B, C> {
    final public A first;
    final public B second;
    final public C third;

    public Tuple3(A first, B second, C third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tuple3)) return false;

        Tuple3<?, ?, ?> tuple3 = (Tuple3<?, ?, ?>) o;

        if (first != null ? !first.equals(tuple3.first) : tuple3.first != null) return false;
        if (second != null ? !second.equals(tuple3.second) : tuple3.second != null) return false;
        return !(third != null ? !third.equals(tuple3.third) : tuple3.third != null);

    }

    @Override
    public int hashCode() {
        int result = first != null ? first.hashCode() : 0;
        result = 31 * result + (second != null ? second.hashCode() : 0);
        result = 31 * result + (third != null ? third.hashCode() : 0);
        return result;
    }
}
