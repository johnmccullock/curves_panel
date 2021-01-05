package main.gui.custom;

import java.awt.geom.Point2D;

public interface CurvesPanelModel<T extends Number>
{
	abstract void setMinimum(T min);
	abstract T getMinimum();
	abstract void setMaximum(T min);
	abstract T getMaximum();
	abstract void setNumValues(int num);
	abstract int getNumValues();
	abstract void setValue(int index, T value);
	abstract void setValue(int index, double percentage);
	abstract T getValue(int index);
	abstract Point2D.Double getPosition(int x, int y);
	abstract int getIndex(double percentage);
	abstract T[] getLUT();
}
