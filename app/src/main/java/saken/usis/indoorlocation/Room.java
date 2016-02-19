package saken.usis.indoorlocation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/*
    Room View:
    Shows points where beacons located and user's approximate location
 */

public class Room extends View {
    protected static final String TAG = "Indoor";
    Paint paint = new Paint();
    Paint circle = new Paint();
    Paint Loc = new Paint();
    Paint loc = new Paint();

    //Stores beacons' locations
    static List<Point> points;
    //True when user puts a new beacon
    static boolean edit;
    //Variables to store user's location
    static int currX = -1;
    static int currY = -1;
    static int X = -1;
    static int Y = -1;

    //Class constructors
    public Room(Context context) {
        super(context);
        setupPaint();
    }

    public Room(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setupPaint();
    }

    public Room(Context context, AttributeSet attributeSet, int d) {
        super(context, attributeSet, d);
        setupPaint();
    }

    //Overrided onDraw method which draws beacons and user's position onto the screen
    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        for (Point p : points) {
            canvas.drawCircle(p.x, p.y, 30, circle);
        }
        if (currX != -1 || currY != -1) canvas.drawCircle(currX, currY, 45, Loc);
        if (X != -1 || Y != -1) canvas.drawCircle(X, Y, 45, loc);
    }

    //Handles beacon placement
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && edit) {
            float touchX = event.getX();
            float touchY = event.getY();
            points.add(new Point(Math.round(touchX), Math.round(touchY)));
            edit = false;
            Log.d(TAG, "Point added: " + touchX + ", " + touchY);
            // indicate view should be redrawn
            postInvalidate();
            return true;
        }
        return false;
    }

    //Initialization of variables
    private void setupPaint() {
        //View rectangle initialization
        paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setAntiAlias(true);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        //User location circle initialization #1
        Loc = new Paint();
        Loc.setColor(Color.BLUE);
        Loc.setAntiAlias(true);
        Loc.setStrokeWidth(5);
        Loc.setStyle(Paint.Style.STROKE);
        Loc.setStrokeJoin(Paint.Join.ROUND);
        Loc.setStrokeCap(Paint.Cap.ROUND);
        //Beacon circle initialization
        circle.setColor(Color.RED);
        circle.setStyle(Paint.Style.FILL);

        points = new ArrayList<>();

        edit = false;
        //User location circle initialization #1
        loc = new Paint();
        loc.setColor(Color.GREEN);
        loc.setAntiAlias(true);
        loc.setStrokeWidth(5);
        loc.setStyle(Paint.Style.STROKE);
        loc.setStrokeJoin(Paint.Join.ROUND);
        loc.setStrokeCap(Paint.Cap.ROUND);

        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    //Method to make canvas editable
    public static void setEdit() {
        edit = true;
    }

    //Clear all elements from the view
    public static void clear() {
        points.clear();
        currX = currY = X = Y = -1;
    }

}
