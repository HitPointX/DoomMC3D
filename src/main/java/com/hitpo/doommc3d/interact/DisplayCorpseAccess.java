package com.hitpo.doommc3d.interact;

public interface DisplayCorpseAccess {
    void setSlideVx(double vx);
    double getSlideVx();
    void setSlideVy(double vy);
    double getSlideVy();
    void setSlideVz(double vz);
    double getSlideVz();

    void setSlideRemaining(int ticks);
    int getSlideRemaining();

    void setSlideDrag(double drag);
    double getSlideDrag();
}
