package com.dji.FPVDemo;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ColorBlobDetector {
    // Lower and Upper bounds for range checking in HSV color space
    private Scalar mLowerBound = new Scalar(0);
    private Scalar mUpperBound = new Scalar(0);
    private Scalar min_hsvColor = new Scalar(0);
    private Scalar max_hsvColor = new Scalar(0);
    // Minimum contour area in percent for contours filtering
    private static double mMinContourArea = 0.1;
    // Color radius for range checking in HSV color space
    private Scalar mColorRadius = new Scalar(25,30,30,0);
    private Mat mSpectrum = new Mat();
    private List<MatOfPoint> mContours = new ArrayList<MatOfPoint>();

    // Cache
    Mat mPyrDownMat = new Mat();
    Mat mHsvMat = new Mat();
    Mat mMask = new Mat();
    Mat mDilatedMask = new Mat();
    Mat mHierarchy = new Mat();
    //New variables
    Mat original_frame;
    Mat bounding_rect_rgba;
    Mat bounding_rect_hsv = new Mat();
    Scalar new_hsvColor = new Scalar(0);
    Scalar temp = new Scalar(0);
    boolean start = false;

    //Very important!!!!  -> in order to reset new_hsvColor
    public void resetStart(){start = false;}

    public void setColorRadius(Scalar radius) {
        mColorRadius = radius;
    }

    public void setHsvColor(Scalar hsvColor) {
        if (!start){
            start = true;
            new_hsvColor = hsvColor;
        }

        double minH = (hsvColor.val[0] >= mColorRadius.val[0]) ? hsvColor.val[0]-mColorRadius.val[0] : 0;
        double maxH = (hsvColor.val[0]+mColorRadius.val[0] <= 255) ? hsvColor.val[0]+mColorRadius.val[0] : 255;

        mLowerBound.val[0] = minH;
        mUpperBound.val[0] = maxH;

        mLowerBound.val[1] = (hsvColor.val[1] >= mColorRadius.val[1]) ? hsvColor.val[1]-mColorRadius.val[1] : 0;
        mUpperBound.val[1] = (hsvColor.val[1]+mColorRadius.val[1] <= 255) ? hsvColor.val[1]+mColorRadius.val[1] : 255;

        mLowerBound.val[2] = (hsvColor.val[2] >= mColorRadius.val[2]) ? hsvColor.val[2]-mColorRadius.val[2] : 0;
        mUpperBound.val[2] = (hsvColor.val[2]+mColorRadius.val[2] <= 255) ? hsvColor.val[2]+mColorRadius.val[2] : 255;

        mLowerBound.val[3] = 0;
        mUpperBound.val[3] = 255;

        //Set max and min bounding condition, so that the color won't change too much
        for (int i=0;i<4;i++){
            min_hsvColor.val[i] = mLowerBound.val[i];
            max_hsvColor.val[i] = mUpperBound.val[i];
        }

        Mat spectrumHsv = new Mat(1, (int)(maxH-minH), CvType.CV_8UC3);

        for (int j = 0; j < maxH-minH; j++) {
            byte[] tmp = {(byte)(minH+j), (byte)255, (byte)255};
            spectrumHsv.put(0, j, tmp);
        }

        Imgproc.cvtColor(spectrumHsv, mSpectrum, Imgproc.COLOR_HSV2RGB_FULL, 4);
    }

    public Mat getSpectrum() {
        return mSpectrum;
    }

    public void setMinContourArea(double area) {
        mMinContourArea = area;
    }

    public void process(Mat rgbaImage) {
        original_frame = rgbaImage;
        Imgproc.pyrDown(rgbaImage, mPyrDownMat);
        Imgproc.pyrDown(mPyrDownMat, mPyrDownMat);

        Imgproc.cvtColor(mPyrDownMat, mHsvMat, Imgproc.COLOR_RGB2HSV_FULL);

        Core.inRange(mHsvMat, mLowerBound, mUpperBound, mMask);
        Imgproc.dilate(mMask, mDilatedMask, new Mat());

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();

        Imgproc.findContours(mDilatedMask, contours, mHierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        // Find max contour area
        double maxArea = 0;
        Iterator<MatOfPoint> each = contours.iterator();
        while (each.hasNext()) {
            MatOfPoint wrapper = each.next();
            double area = Imgproc.contourArea(wrapper);
            if (area > maxArea)
                maxArea = area;
        }

        MatOfPoint2f approxCurve = new MatOfPoint2f();

        // Filter contours by area and resize to fit the original image size
        mContours.clear();
        each = contours.iterator();
        while (each.hasNext()) {
            MatOfPoint contour = each.next();
            if (Imgproc.contourArea(contour) > 0.9 * maxArea) {
                Core.multiply(contour, new Scalar(4, 4), contour);
                mContours.add(contour);

                //Todo: Error correction part (Could use the bounding rectangular, maybe with noise removal)
                /*new_hsvColor = Core.sumElems(contour);
                int pointCount =
                for (int i = 0; i < new_hsvColor.val.length; i++)
                    new_hsvColor.val[i] /= pointCount;  */

                //Contour processing part
                //Convert contours(i) from MatOfPoint to MatOfPoint2f
                MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());

                //Processing on mMOP2f1 which is in type MatOfPoint2f
                double approxDistance = Imgproc.arcLength(contour2f, true)*0.02;
                Imgproc.approxPolyDP(contour2f, approxCurve, approxDistance, true);

                //Convert back to MatOfPoint
                MatOfPoint points = new MatOfPoint( approxCurve.toArray() );

                // Get bounding rect of contour
                Rect rect = Imgproc.boundingRect(points);

                // draw enclosing rectangle (all same color, but you could use variable i to make them unique)
                Imgproc.rectangle(original_frame, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(255, 0, 0, 255), 3);

                //Todo: use the bounding rectangular to calculate average intensity (turn the pixels out of the contour to new_hsvColor)
                //Just change the boundary values would be enough
                bounding_rect_rgba = original_frame.submat(rect);
                Imgproc.cvtColor(bounding_rect_rgba, bounding_rect_hsv, Imgproc.COLOR_RGB2HSV_FULL);
                //Todo: Pixels outside the contour will be changed to new_hsvColor
                for (int i=0; i< bounding_rect_hsv.rows();i+=10){
                    for (int j=0; j<bounding_rect_hsv.cols();j+=10){
                        double[] data = bounding_rect_hsv.get(i, j);
                        for (int k = 0; k < 3; k++){
                            if (data[k] > new_hsvColor.val[k] + 30 || data[k] < new_hsvColor.val[k] - 30) {
                                data[k] = new_hsvColor.val[k];
                            }
                        }
                        bounding_rect_hsv.put(i, j, data); //Puts element back into matrix
                    }
                }
                temp = Core.sumElems(bounding_rect_hsv);
                int pointCount = rect.width * rect.height;
                for (int i = 0; i < temp.val.length; i++) {
                    temp.val[i] /= pointCount;
                    new_hsvColor.val[i] = (new_hsvColor.val[i] + temp.val[i]) / 2;
                }

                // Update the parameters
                for (int i=0;i<3;i++){
                    if (new_hsvColor.val[i] > max_hsvColor.val[i]) //Limit the bounding condition
                        new_hsvColor.val[i] = max_hsvColor.val[i];
                    if (new_hsvColor.val[i] < min_hsvColor.val[i])
                        new_hsvColor.val[i] = min_hsvColor.val[i];
                }

                mLowerBound.val[0] = (new_hsvColor.val[0] >= mColorRadius.val[0]) ? new_hsvColor.val[0]-mColorRadius.val[0] : 0;
                mUpperBound.val[0] = (new_hsvColor.val[0]+mColorRadius.val[0] <= 255) ? new_hsvColor.val[0]+mColorRadius.val[0] : 255;

                mLowerBound.val[1] = (new_hsvColor.val[1] >= mColorRadius.val[1]) ? new_hsvColor.val[1]-mColorRadius.val[1] : 0;
                mUpperBound.val[1] = (new_hsvColor.val[1]+mColorRadius.val[1] <= 255) ? new_hsvColor.val[1]+mColorRadius.val[1] : 255;

                mLowerBound.val[2] = (new_hsvColor.val[2] >= mColorRadius.val[2]) ? new_hsvColor.val[2]-mColorRadius.val[2] : 0;
                mUpperBound.val[2] = (new_hsvColor.val[2]+mColorRadius.val[2] <= 255) ? new_hsvColor.val[2]+mColorRadius.val[2] : 255;
            }
        }
    }

    public List<MatOfPoint> getContours() {
        return mContours;
    }

    public Scalar get_new_hsvColor(){
        return new_hsvColor;
    }
}
