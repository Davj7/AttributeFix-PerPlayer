package net.darkhax.attributefix.common.impl;

/**
 * A per-player override of an attribute's range. Either bound may be {@code null}, meaning "no
 * override for that bound" (the global limit keeps governing it). Immutable; use {@link #withMin}
 * and {@link #withMax} to derive updated copies so setting one bound never wipes the other.
 */
public record AttributeLimit(Double min, Double max) {

    public AttributeLimit withMin(double value) {
        return new AttributeLimit(value, this.max);
    }

    public AttributeLimit withMax(double value) {
        return new AttributeLimit(this.min, value);
    }

    public boolean isEmpty() {
        return this.min == null && this.max == null;
    }
}
