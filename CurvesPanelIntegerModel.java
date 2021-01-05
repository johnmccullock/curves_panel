package main.gui.custom;

import java.awt.geom.Point2D;

public class CurvesPanelIntegerModel implements CurvesPanelModel<Integer>
{
	private int mMin = 0;
	private int mMax = 0;
	private int[] mLUT = null;
	
	public CurvesPanelIntegerModel(int numValues)
	{
		this.setNumValues(numValues);
	}
	
	@Override
	public void setMinimum(Integer min)
	{
		this.mMin = min;
		return;
	}
	
	@Override
	public Integer getMinimum()
	{
		return this.mMin;
	}
	
	@Override
	public void setMaximum(Integer max)
	{
		this.mMax = max;
		return;
	}
	
	@Override
	public Integer getMaximum()
	{
		return this.mMax;
	}
	
	@Override
	public void setNumValues(int num)
	{
		this.mLUT = new int[num];
	}
	
	@Override
	public int getNumValues()
	{
		return this.mLUT.length;
	}

	@Override
	public void setValue(int index, Integer value)
	{
		this.mLUT[index] = value;
		return;
	}
	
	@Override
	public void setValue(int index, double percentage)
	{
		this.mLUT[index] = this.clamp(this.mMin, this.mMax, (int)Math.round(percentage * this.mMax));
	}
	
	public int clamp(int lowerBound, int upperBound, int value)
	{
		return (value < lowerBound) ? lowerBound : (value > upperBound) ? upperBound : value;
	}

	@Override
	public Integer getValue(int index)
	{
		return this.mLUT[index];
	}
	
	public Point2D.Double getPosition(int x, int y)
	{
		return new Point2D.Double(x / (double)(this.mMax - this.mMin), y / (double)(this.mMax - this.mMin));
	}
	
	@Override
	public int getIndex(double percentage)
	{
		return this.clamp(this.mMin, this.mMax, (int)Math.round(percentage * (this.mMax - this.mMin)));
	}

	@Override
	public Integer[] getLUT()
	{
		Integer[] results = new Integer[this.mLUT.length];
		for(int i = 0; i < this.mLUT.length; i++)
		{
			results[i] = this.mLUT[i];
		}
		return results;
	}

}
