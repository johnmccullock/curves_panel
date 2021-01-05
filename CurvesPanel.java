package main.gui.custom;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import java.util.Collections;
import java.util.Vector;

import javax.swing.JPanel;
import javax.swing.UIManager;

@SuppressWarnings("serial")
public class CurvesPanel extends JPanel
{
	private static final int DEFAULT_GRIP_RADIUS = 5;
	private static final int DEFAULT_GRID_STROKE = 1;
	private static final int DEFAULT_SPLINE_STROKE = 2;
	private static final Color DEFAULT_PANEL_BACKGROUND = UIManager.getColor("Panel.background");
	private static final Color DEFAULT_GRAPH_BACKGROUND = new Color(255, 255, 255, 255);
	private static final Color DEFAULT_GRID_COLOR = new Color(160, 160, 160, 255);
	private static final Color DEFAULT_SPLINE_COLOR = new Color(96, 96, 96, 255);
	private static final Color DEFAULT_GRIP_COLOR = new Color(0, 0, 0, 255);
	private static final Color DEFAULT_CURRENT_GRIP_COLOR = new Color(255, 0, 0, 255);
	
	private Rectangle mGraphBounds = new Rectangle();
	private Rectangle mInputGuide = new Rectangle();
	private Rectangle mOutputGuide = new Rectangle();
	private Vector<Grip> mGrips = new Vector<Grip>();
	private int mGripRadius = DEFAULT_GRIP_RADIUS;
	private int mGridStroke = DEFAULT_GRID_STROKE;
	private int mSplineStroke = DEFAULT_SPLINE_STROKE;
	private Color mPanelBackground = DEFAULT_PANEL_BACKGROUND;
	private Color mGraphBackground = DEFAULT_GRAPH_BACKGROUND;
	private Color mGridColor = DEFAULT_GRID_COLOR;
	private Color mSplineColor = DEFAULT_SPLINE_COLOR;
	private Color mGripColor = DEFAULT_GRIP_COLOR;
	private Color mCurrentGripColor = DEFAULT_CURRENT_GRIP_COLOR;
	private Grip mCurrent = null;
	private boolean mIsAdjusting = false;
	private BufferedImage mGraphDecoration = null;
	private Cursor mNormalCursor = new Cursor(Cursor.DEFAULT_CURSOR);
	private Cursor mHoverCursor = new Cursor(Cursor.HAND_CURSOR);
	private CurvesPanelModel<?> mModel = null;
	private Vector<CurvesPanelModelObserver> mObservers = new Vector<CurvesPanelModelObserver>();
	
	public CurvesPanel()
	{
		this.initialize();
		return;
	}
	
	private void initialize()
	{
		this.mGrips.add(new Grip(0.0, 0.0)); // lower bound
		this.mGrips.add(new Grip(0.0, 0.0)); // upper bound
		this.addMouseListener(this.createPrimaryMouseListener());
		this.addMouseMotionListener(this.createPrimaryMouseMotionListener());
		this.addComponentListener(this.createPrimaryComponentListener());
		return;
	}
	
	@Override
	public void doLayout()
	{
		super.doLayout();
		this.mGraphBounds.width = this.getWidth() - (this.mGripRadius * 2);
		this.mGraphBounds.height = this.mGraphBounds.width;
		this.mGraphBounds.x = this.getWidth() - this.mGraphBounds.width;
		this.mGraphBounds.y = 0;
		
		this.mOutputGuide.x = 0;
		this.mOutputGuide.y = this.mGripRadius;
		this.mOutputGuide.width = this.mGripRadius;
		this.mOutputGuide.height = this.mGraphBounds.height - (this.mOutputGuide.y + this.mGripRadius);
		
		this.mInputGuide.x = this.mGraphBounds.x + this.mGripRadius;
		this.mInputGuide.y = this.mGraphBounds.y + this.mGraphBounds.height + this.mGripRadius;
		this.mInputGuide.width = this.mGraphBounds.width - (this.mGripRadius * 2);
		this.mInputGuide.height = this.mGripRadius;
		
		/*
		 * ToDo: find a way to preserve point positions during component resize.
		 */
		
		this.mGrips.firstElement().x = this.mGraphBounds.x + this.mGripRadius;
		this.mGrips.firstElement().y = (this.mGraphBounds.y + this.mGraphBounds.height) - this.mGripRadius;
		this.mGrips.lastElement().x = (this.mGraphBounds.x + this.mGraphBounds.width) - this.mGripRadius;
		this.mGrips.lastElement().y = this.mGraphBounds.y + this.mGripRadius;
		return;
	}
	
	public void updateModel()
	{
		Collections.sort(this.mGrips);
		
		double[] xs = new double[this.mGrips.size()];
		double[] ys = new double[this.mGrips.size()];
		double[] ks = new double[this.mGrips.size()];
		for(int i = 0; i < this.mGrips.size(); i++)
		{
			Grip grip = this.mGrips.get(i);
			xs[i] = grip.x;
			ys[i] = grip.y;
			ks[i] = 1;
		}
		ks = this.getNaturalKs(xs, ys, ks);
		double minx = this.mGrips.get(0).x;
		double maxx = this.mGrips.get(mGrips.size() - 1).x;
		double increment = (maxx - minx) / (double)(this.mModel.getNumValues());
		int j = 0;
		for(double i = minx; i <= maxx; i += increment, j++)
		{
			double value = this.clamp(this.mGraphBounds.y + this.mGripRadius, (this.mGraphBounds.y + this.mGraphBounds.height) - this.mGripRadius, this.evalSpline(i, xs, ys, ks));
			double dy = (((this.mGraphBounds.y + this.mGraphBounds.height) - this.mGripRadius) - value) / (double)((this.mGraphBounds.height - this.mGraphBounds.y) - (this.mGripRadius * 2));
			if(j == 0){
				this.mModel.setValue(j, 0.0);
			}else if(j == this.mModel.getNumValues() - 1){
				this.mModel.setValue(j, 1.0);
				// I think it's impossible to make i reach maxx at the same time j reaches the array limit.
				// ToDo: fix this so it does.
				break;
			}else{
				this.mModel.setValue(j, dy);
			}
		}
		return;
	}
	
	@Override
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D)g;
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		Collections.sort(this.mGrips);
		
		double[] xs = new double[this.mGrips.size()];
		double[] ys = new double[this.mGrips.size()];
		double[] ks = new double[this.mGrips.size()];
		for(int i = 0; i < this.mGrips.size(); i++)
		{
			Grip grip = this.mGrips.get(i);
			xs[i] = grip.x;
			ys[i] = grip.y;
			ks[i] = 1;
		}
		ks = this.getNaturalKs(xs, ys, ks);
		
		g2d.setPaint(this.mPanelBackground);
		g2d.fillRect(0, 0, this.getWidth(), this.getHeight());
		g2d.setPaint(this.mGraphBackground);
		g2d.fillRect(this.mGraphBounds.x, this.mGraphBounds.y, this.mGraphBounds.width, this.mGraphBounds.height);
		this.renderGraphDecoration(g2d);
		this.renderGrid(g2d);
		this.renderInputGuide(g2d);
		this.renderOutputGuide(g2d);
		
		GeneralPath path = new GeneralPath();
		double minx = this.mGrips.get(0).x;
		double maxx = this.mGrips.get(this.mGrips.size() - 1).x;
		
		path.moveTo((int)Math.round(minx), this.evalSpline(minx, xs, ys, ks));
		for(double i = minx + 1.0; i <= maxx; i += 1.0)
		{
			path.lineTo(i, this.clamp(this.mGraphBounds.y + this.mGripRadius, (this.mGraphBounds.y + this.mGraphBounds.height) - this.mGripRadius, this.evalSpline(i, xs, ys, ks)));
		}
		
		g2d.setPaint(this.mSplineColor);
		g2d.setStroke(new BasicStroke(this.mSplineStroke));
		g2d.draw(path);
		
		g2d.setPaint(this.mGripColor);
		for(int i = 0; i < this.mGrips.size(); i++)
		{
			if(i == 0 || i == this.mGrips.size() - 1){
				g2d.setStroke(new BasicStroke(this.mGridStroke));
				g2d.setPaint(new Color(255, 255, 255, 255));
				g2d.fillOval((int)Math.round(this.mGrips.get(i).x - this.mGripRadius), (int)Math.round(this.mGrips.get(i).y - this.mGripRadius), this.mGripRadius * 2, this.mGripRadius * 2);
				g2d.setPaint(this.mGripColor);
				g2d.drawOval((int)Math.round(this.mGrips.get(i).x - this.mGripRadius), (int)Math.round(this.mGrips.get(i).y - this.mGripRadius), this.mGripRadius * 2, this.mGripRadius * 2);
			}else{
				g2d.fillOval((int)Math.round(this.mGrips.get(i).x - this.mGripRadius), (int)Math.round(this.mGrips.get(i).y - this.mGripRadius), this.mGripRadius * 2, this.mGripRadius * 2);
			}
		}
		
		g2d.dispose();
		return;
	}
	
	public void renderGraphDecoration(Graphics2D g2d)
	{
		if(this.mGraphDecoration == null){
			return;
		}
		g2d.drawImage(this.mGraphDecoration, this.mGraphBounds.x, this.mGraphBounds.y, this.mGraphBounds.width, this.mGraphBounds.height, null);
		return;
	}
	
	public void renderInputGuide(Graphics2D g2d)
	{
		g2d.setPaint(new LinearGradientPaint(new Point2D.Float(this.mInputGuide.x, this.mInputGuide.y), 
											new Point2D.Float(this.mInputGuide.x + this.mInputGuide.width, this.mInputGuide.y), 
											new float[]{0F, 1F}, 
											new Color[]{new Color(0, 0, 0, 255), new Color(255, 255, 255, 255)}));
		g2d.fillRect(this.mInputGuide.x, this.mInputGuide.y, this.mInputGuide.width, this.mInputGuide.height);
		return;
	}
	
	public void renderOutputGuide(Graphics2D g2d)
	{
		g2d.setPaint(new LinearGradientPaint(new Point2D.Float(this.mOutputGuide.x, this.mOutputGuide.y), 
											new Point2D.Float(this.mOutputGuide.x, this.mOutputGuide.y + this.mOutputGuide.height), 
											new float[]{0F, 1F}, 
											new Color[]{new Color(255, 255, 255, 255), new Color(0, 0, 0, 255)}));
		g2d.fillRect(this.mOutputGuide.x, this.mOutputGuide.y, this.mOutputGuide.width, this.mOutputGuide.height);
		return;
	}
	
	public void renderGrid(Graphics2D g2d)
	{
		g2d.setStroke(new BasicStroke(this.mGridStroke));
		g2d.setPaint(this.mGridColor);
		for(int i = 0; i < 5; i++)
		{
			int x = this.clamp(0, this.getWidth() - this.mGridStroke, (int)Math.round(this.mGraphBounds.x + (this.mGraphBounds.width * (0.25 * i))));
			g2d.drawLine(x, this.mGraphBounds.y, x, (this.mGraphBounds.y + this.mGraphBounds.height) - this.mGridStroke);
		}
		for(int i = 0; i < 5; i++)
		{
			int y = this.clamp(0, (this.mGraphBounds.y + this.mGraphBounds.height) - this.mGridStroke, (int)Math.round(this.mGraphBounds.y + (this.mGraphBounds.height * (i * 0.25))));
			g2d.drawLine(this.mGraphBounds.x, y, (this.mGraphBounds.x + this.mGraphBounds.width) - this.mGridStroke, y);
		}
		return;
	}
	
	public double distance(double x1, double y1, double x2, double y2)
	{
		return Math.sqrt(((x2 - x1) * (x2 - x1)) + ((y2 - y1) * (y2 - y1)));
	}
	
	public double clamp(double lowerBound, double upperBound, double value)
	{
		return (value < lowerBound) ? lowerBound : (value > upperBound) ? upperBound : value;
	}
	
	public int clamp(int lowerBound, int upperBound, int value)
	{
		return (value < lowerBound) ? lowerBound : (value > upperBound) ? upperBound : value;
	}
	
	/**
	 * @param A 2D-matrix for input.
	 * @param x Array for output solutions.
	 * @return 
	 * @author Ivan Kutskir - http://blog.ivank.net/interpolation-with-cubic-splines.html
	 */
	public double[] solve(double[][] A, double[] x)
	{
		int m = A.length;
		for(int k = 0; k < m; k++)	// column
		{
			/*
			 *  pivot for column
			 */
			int i_max = 0;
			double vali = Double.NEGATIVE_INFINITY;
			for(int i = k; i < m; i++)
			{
				if(Math.abs(A[i][k])>vali){
					i_max = i; 
					vali = Math.abs(A[i][k]);
				}
			}
			this.swapRows(A, k, i_max);
			
			/*
			 * for all rows below pivot
			 */
			for(int i = k + 1; i < m; i++)
			{
				double cf = (A[i][k] / A[k][k]);
				for(int j = k; j < m + 1; j++)
				{
					A[i][j] -= A[k][j] * cf;
				}
			}
		}
		
		for(int i = m - 1; i >= 0; i--)	// rows = columns
		{
			double v = A[i][m] / A[i][i];
			x[i] = v;
			for(int j = i - 1; j >= 0; j--)	// rows
			{
				A[j][m] -= A[j][i] * v;
				A[j][i] = 0.0;
			}
		}
		return x;
	}
	
	/**
	 * 
	 * @param xs array of x-values.
	 * @param ys array of y-values.
	 * @param ks array of k-values.
	 * @return array of k-values.
	 * @author Ivan Kutskir - http://blog.ivank.net/interpolation-with-cubic-splines.html
	 */
	public double[] getNaturalKs(double[] xs, double[] ys, double[] ks)	// in x values, in y values, out k values
	{
		int n = xs.length - 1;
		double[][] m = this.zerosMat(n + 1, n + 2);
			
		for(int i = 1; i < n; i++)	// rows
		{
			m[i][i - 1] = 1 / (xs[i] - xs[i - 1]);
			
			m[i][i] = 2 * (1 / (xs[i] - xs[i - 1]) + 1 / (xs[i + 1] - xs[i]));
			
			m[i][i + 1] = 1 / (xs[i + 1] - xs[i]);
			
			m[i][n + 1] = 3 * ((ys[i] - ys[i - 1]) / ((xs[i] - xs[i - 1]) * (xs[i] - xs[i - 1]))  +  (ys[i + 1] - ys[i]) / ((xs[i + 1] - xs[i]) * (xs[i + 1] - xs[i])));
		}
		
		m[0][0] = 2 / (xs[1] - xs[0]);
		m[0][1] = 1 / (xs[1] - xs[0]);
		m[0][n + 1] = 3 * (ys[1] - ys[0]) / ((xs[1] - xs[0]) * (xs[1] - xs[0]));
		
		m[n][n - 1] = 1 / (xs[n] - xs[n - 1]);
		m[n][n] = 2 / (xs[n] - xs[n - 1]);
		m[n][n + 1] = 3 * (ys[n] - ys[n - 1]) / ((xs[n] - xs[n - 1]) * (xs[n] - xs[n - 1]));
			
		return this.solve(m, ks);
	}
	
	/**
	 * Find position of point on spline for given input parameters.
	 * @param x interval (time)
	 * @param xs array of x-values.
	 * @param ys array of y-values.
	 * @param ks array of k-values.
	 * @return corresponding position for spline point.
	 * @author Ivan Kutskir - http://blog.ivank.net/interpolation-with-cubic-splines.html
	 */
	public double evalSpline(double x, double[] xs, double[] ys, double[] ks)
	{
		int i = 1;
		while(xs[i] < x) i++;
		
		double t = (x - xs[i - 1]) / (xs[i] - xs[i - 1]);
		
		double a =  ks[i - 1] * (xs[i] - xs[i - 1]) - (ys[i] - ys[i - 1]);
		double b = -ks[i] * (xs[i] - xs[i - 1]) + (ys[i] - ys[i - 1]);
		
		double q = (1 - t) * ys[i - 1] + t * ys[i] + t * (1 - t) * (a * (1 - t) + b * t);
		return q;
	}
	
	public double[][] zerosMat(int rows, int cols)
	{
		double[][] m = new double[rows][cols];
		for(int i = 0; i < rows; i++)
		{
			for(int j = 0; j < cols; j++) 
				m[i][j] = 0.0;
		} 
		return m;
	}
	
	public double[][] swapRows(double[][] m, int r, int t)
	{
		double[] p = m[r]; 
		m[r] = m[t]; 
		m[t] = p;
		return m;
	}
	
	/**
	 * For programmatically moving the current grip.
	 * @param x
	 * @param y
	 */
	public void setCurrentGripPos(int x, int y)
	{
		if(this.mCurrent == null){
			return;
		}
		Point2D.Double pos = this.mModel.getPosition(x, y);
		double width = this.mGraphBounds.width - (this.mGripRadius * 2);
		double height = this.mGraphBounds.height - (this.mGripRadius * 2);
		double xPos = (this.mGraphBounds.x + this.mGripRadius) + (pos.x * width);
		double yPos = ((this.mGraphBounds.y + this.mGraphBounds.height) - this.mGripRadius) - (pos.y * height);
		xPos = this.clamp(this.mGraphBounds.x + this.mGripRadius, (this.mGraphBounds.x + this.mGraphBounds.width) - this.mGripRadius, xPos);
		yPos = this.clamp(this.mGraphBounds.y + this.mGripRadius, (this.mGraphBounds.y + this.mGraphBounds.height) - this.mGripRadius, yPos);
		this.mCurrent.x = xPos;
		this.mCurrent.y = yPos;
		this.repaint();
		this.updateModel();
		return;
	}
	
	public Point getCurrentGripPos()
	{
		if(this.mCurrent == null){
			return null;
		}
		double width = this.mGraphBounds.width - (this.mGripRadius * 2);
		double dx = (this.mCurrent.x - (this.mGraphBounds.x + this.mGripRadius)) / width;
		double dy = (((this.mGraphBounds.y + this.mGraphBounds.height) - this.mGripRadius) - this.mCurrent.y) / (double)(this.mGraphBounds.height - this.mGraphBounds.y - (this.mGripRadius * 2));
		int x = this.mModel.getIndex(dx);
		int y = this.mModel.getIndex(dy);
		return new Point(x, y);
	}
	
	public void resetGraph()
	{
		for(int i = this.mGrips.size() - 1; i >= 0; i--)
		{
			if(this.mGrips.get(i).equals(this.mGrips.firstElement())){
				continue;
			}
			if(this.mGrips.get(i).equals(this.mGrips.lastElement())){
				continue;
			}
			this.mGrips.remove(i);
		}
		CurvesPanel.this.repaint();
		CurvesPanel.this.updateModel();
		CurvesPanel.this.notifyAllObservers();
		return;
	}
	
	public Rectangle getGraphBounds()
	{
		return new Rectangle(this.mGraphBounds);
	}
	
	public void setGripRadius(int radius)
	{
		this.mGripRadius = radius;
		return;
	}
	
	public int getGripRadius()
	{
		return this.mGripRadius;
	}
	
	public void setGridStrokeThickness(int stroke)
	{
		this.mGridStroke = stroke;
		return;
	}
	
	public int getGridStrokeThickness()
	{
		return this.mGridStroke;
	}
	
	public void setSplineStrokeThickness(int stroke)
	{
		this.mSplineStroke = stroke;
		return;
	}
	
	public int getSplineStrokeThickness()
	{
		return this.mSplineStroke;
	}
	
	@Override
	public void setBackground(Color background)
	{
		super.setBackground(background);
		this.mPanelBackground = background;
		return;
	}
	
	@Override
	public Color getBackground()
	{
		return this.mPanelBackground;
	}
	
	public void setGraphBackground(Color graph)
	{
		this.mGraphBackground = graph;
		return;
	}
	
	public Color getGraphBackground()
	{
		return this.mGraphBackground;
	}
	
	public void setGridColor(Color grid)
	{
		this.mGridColor = grid;
		return;
	}
	
	public Color getGridColor()
	{
		return this.mGridColor;
	}
	
	public void setSplineColor(Color spline)
	{
		this.mSplineColor = spline;
		return;
	}
	
	public Color getSplineColor()
	{
		return this.mSplineColor;
	}
	
	public void setGripColor(Color grip)
	{
		this.mGripColor = grip;
		return;
	}
	
	public Color getGripColor()
	{
		return this.mGripColor;
	}
	
	public void setCurrentGripColor(Color current)
	{
		this.mCurrentGripColor = current;
		return;
	}
	
	public Color getCurrentGripColor()
	{
		return this.mCurrentGripColor;
	}
	
	public void setIsAdjusting(boolean isAdjusting)
	{
		this.mIsAdjusting = isAdjusting;
		return;
	}
	
	public boolean getIsAdjusting()
	{
		return this.mIsAdjusting;
	}
	
	public void setNormalCursor(Cursor normal)
	{
		this.mNormalCursor = normal;
		return;
	}
	
	public Cursor getNormalCursor()
	{
		return this.mNormalCursor;
	}
	
	public void setHoverCursor(Cursor hover)
	{
		this.mHoverCursor = hover;
		return;
	}
	
	public Cursor getHoverCursor()
	{
		return this.mHoverCursor;
	}
	
	public void setGraphDecoration(BufferedImage image)
	{
		this.mGraphDecoration = image;
		return;
	}
	
	public BufferedImage getGraphDecoration()
	{
		return this.mGraphDecoration;
	}
	
	public void setModel(CurvesPanelModel<?> model)
	{
		this.mModel = model;
	}
	
	public CurvesPanelModel<?> getModel()
	{
		return this.mModel;
	}
	
	public void addModelObserver(CurvesPanelModelObserver observer)
	{
		this.mObservers.add(observer);
		return;
	}
	
	public void removeModelObserver(CurvesPanelModelObserver observer)
	{
		for(int i = this.mObservers.size() - 1; i >= 0; i--)
		{
			if(this.mObservers.get(i).equals(observer)){
				this.mObservers.remove(i);
			}
		}
		return;
	}
	
	public void notifyAllObservers()
	{
		for(CurvesPanelModelObserver obs : this.mObservers)
		{
			obs.curveChanged();
		}
		return;
	}
	
	private ComponentListener createPrimaryComponentListener()
	{
		return new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				resetGraph();
				int square = Math.max(CurvesPanel.this.getWidth(), CurvesPanel.this.getHeight());
				CurvesPanel.this.setSize(square, square);
				CurvesPanel.this.repaint();
				CurvesPanel.this.updateModel();
				CurvesPanel.this.notifyAllObservers();
				return;
			}
		};
	}
	
	private MouseListener createPrimaryMouseListener()
	{
		return new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				mIsAdjusting = true;
				boolean noGrip = true;
				for(Grip g : mGrips)
				{
					if(g.equals(mGrips.firstElement())){
						continue;
					}
					if(g.equals(mGrips.lastElement())){
						continue;
					}
					if(distance(e.getX(), e.getY(), g.x, g.y) <= mGripRadius){
						mCurrent = g;
						noGrip = false;
						break;
					}
				}
				if(noGrip){
					Grip g = new Grip(e.getX(), e.getY());
					mGrips.add(g);
					mCurrent = g;
					CurvesPanel.this.setCursor(mHoverCursor);
				}
				CurvesPanel.this.repaint();
				CurvesPanel.this.updateModel();
				CurvesPanel.this.notifyAllObservers();
				return;
			}
			
			@Override
			public void mouseReleased(MouseEvent e)
			{
				CurvesPanel.this.repaint();
				CurvesPanel.this.updateModel();
				mIsAdjusting = false;
				notifyAllObservers();
				return;
			}
		};
	}
	
	private MouseMotionListener createPrimaryMouseMotionListener()
	{
		return new MouseMotionListener()
		{
			@Override
			public void mouseDragged(MouseEvent e)
			{
				if(mCurrent == null){
					return;
				}
				mCurrent.x = clamp(mGraphBounds.x + mGripRadius, (mGraphBounds.x + mGraphBounds.width) - mGripRadius, e.getX());
				mCurrent.y = clamp(mGraphBounds.y + mGripRadius, (mGraphBounds.y + mGraphBounds.height) - mGripRadius, e.getY());
				CurvesPanel.this.repaint();
				CurvesPanel.this.updateModel();
				CurvesPanel.this.notifyAllObservers();
			}
			
			@Override
			public void mouseMoved(MouseEvent e)
			{
				for(Grip g : mGrips)
				{
					if(g.equals(mGrips.firstElement())){
						CurvesPanel.this.setCursor(mNormalCursor);
					}else if(g.equals(mGrips.lastElement())){
						CurvesPanel.this.setCursor(mNormalCursor);
					}else if(distance(e.getX(), e.getY(), g.x, g.y) <= mGripRadius){
						CurvesPanel.this.setCursor(mHoverCursor);
						break;
					}else{
						CurvesPanel.this.setCursor(mNormalCursor);
					}
				}
				return;
			}
		};
	}
	
	private class Grip implements Comparable<Grip>
	{
		public double x = 0.0;
		public double y = 0.0;
		
		public Grip() { return; }
		
		public Grip(double x, double y)
		{
			this.x = x;
			this.y = y;
		}
		
		@Override
		public int compareTo(Grip that)
		{
			if(this.x < that.x){
				return -1;
			}else if(this.x > that.x){
				return 1;
			}else{
				return 0;
			}
		}
	}
}
