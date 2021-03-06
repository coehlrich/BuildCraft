package buildcraft.lib.recipe;

import java.util.Arrays;

import net.minecraft.item.ItemStack;

public class ChangingObject<T> {
    protected final T[] options;
    private final int hash;
    private int timeGap = 1000;

    public ChangingObject(T[] options) {
        this.options = options;
        hash = computeHash();
    }

    protected int computeHash() {
        return Arrays.hashCode(options);
    }

    /** @return The {@link ItemStack} that should be displayed at the current time. */
    public T get() {
        return get(0);
    }

    public T get(int indexOffset) {
        long now = (System.currentTimeMillis() / timeGap) % options.length;
        int i = (int) now + indexOffset;
        return options[i % options.length];
    }

    /** Sets the time gap between different stacks showing, in milliseconds. Defaults to 1000 (1 second) */
    public void setTimeGap(int timeGap) {
        this.timeGap = timeGap;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (obj.getClass() != getClass()) return false;
        ChangingObject<?> other = (ChangingObject<?>) obj;
        if (hash != other.hash) return false;
        return Arrays.equals(options, other.options);
    }
}
