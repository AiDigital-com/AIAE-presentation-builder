package com.aidigital.reportconstructor.externalservices.google;

/**
 * The on-slide bounding box of one page element, in EMU, already resolved from its size and affine transform.
 *
 * @param left   x of the left edge
 * @param top    y of the top edge
 * @param right  x of the right edge
 * @param bottom y of the bottom edge
 */
public record ElementBounds(double left, double top, double right, double bottom) {

	/**
	 * Tells whether another box lies inside this one, allowing it to overhang every edge by the tolerance so a
	 * chart nudged a hair past its card's border in the template still counts as sitting on that card.
	 *
	 * @param other     the box to test
	 * @param tolerance how far, in EMU, the other box may cross each edge
	 * @return {@code true} when {@code other} lies within this box
	 */
	public boolean contains(ElementBounds other, double tolerance) {
		return other.left >= left - tolerance && other.top >= top - tolerance
				&& other.right <= right + tolerance && other.bottom <= bottom + tolerance;
	}

	/**
	 * Returns the box's area, used to pick the tightest of several cards that all enclose the same heading.
	 *
	 * @return width times height, in EMU squared
	 */
	public double area() {
		return (right - left) * (bottom - top);
	}
}
