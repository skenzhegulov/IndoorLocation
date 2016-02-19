package saken.usis.indoorlocation;


/*
    Simple Kalman Filter implementation.
    May need more calibration of R and Q.
    Method Filter removes the noises.
 */
public class KalmanFilter {
    double R;
    double Q;
    double A;
    double B;
    double C;
    double P;
    double x;

    KalmanFilter(double x0, double r, double q) {
        R = r;
        Q = q;
        A = 1;
        B = 0;
        C = 1;
        P = 1;
        x = x0;
    }

    public void Filter(double z) {
        double predX = x;
        double predP = P + Q;
        double y = z - predX;
        double S = predP + R;
        double K = predP / S;
        x = predX + K * y;
        P = (1. - K * C) * predP;
    }

    public double get() {
        return x;
    }
}
