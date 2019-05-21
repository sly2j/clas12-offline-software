/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.rec.ltcc;

import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.geom.prim.Vector3D;
import java.util.List;
import java.util.ArrayList;

/**
 *
 * @author Sylvester Joosten
 */
public final class LTCCCluster {
    
    /* Hard-coded cluster requirements to define a good cluster */
    static private final double GOOD_CLUSTER_NPHE_MIN = 0.;
    static private final double GOOD_CLUSTER_NPHE_MAX = 10000;
    static private final int GOOD_CLUSTER_NHIT_MIN = 1;
    static private final int GOOD_CLUSTER_NHIT_MAX = 10;
    static private final double CLUSTER_IN_TIME_CUT = 50.; // [ns]
    
    /* Cluster status */
    private enum Status {
        GOOD (0), 
        BAD (-1);     
        private final int code;         
        Status(int b) {
            code = b;
        }
        private int getValue() {
            return code;
        }
        private boolean isGood() {
            return (code >= 0);
        }
        private static Status fromInteger(int b) {
            switch (b) {
                case 0:
                    return GOOD;
                default:
                    return BAD;
            }
        }
    }
    
    /* cluster identifier */
    private final int id;            // cluster ID

    /* cluster position
     * Note: with weighted, I mean Sum(nphe * VALUE) 
     *       to get the actual weighted value, we need to divide by nphe,
     *       which is automatically done by the outward-facing interface.
    */
    private int sector = -1;        // cluster sector
    private double segment = 0;     // nphe weighted segment
    private final Vector3D rawPos;   // nphe weighted PMT-based cluster position
    
    /* theta/phi range of the cluster */
    private double dtheta = 0.;     // nphe weighted
    private double dphi = 0.;       // nphe weighted

    /* hit statistics */
    private int nHitsLeft = 0;      // Left side hits
    private int nHitsRight = 0;     // Right side hits
    private long rawHitsLeft = 0;   // raw PMT hit numbers on the left side
    private long rawHitsRight = 0;  // raw PMT hit number on the right side
    private int nHitsInTime = 0;    // Hits with a timing cut
    
    /* photo-electrons */
    private double nphe = 0;        // total number of photo-electrons
    private double npheInTime;      // nphe with timing cut
    
    /* timing */
    private double time = 0;        // average nphe weighter cluster time
    
    /* status */
    private Status status = Status.GOOD; // cluster status
    
    /* associated track info */
    private int trackID = -1;
    private Vector3D bestPos = null; // best estimate of cluster position
                                     // based on track position

    
    static public List<LTCCCluster> load(DataEvent event) {
        return load(event, false);
    }
    static public List<LTCCCluster> load(DataEvent event, boolean requireGood) {
        List<LTCCCluster> clusters = new ArrayList<>();
        if (event.hasBank("LTCC::clusters")) {
            DataBank bank = event.getBank("LTCC::clusters");
            for (int i = 0; i < bank.rows(); ++i) {
                LTCCCluster cluster = new LTCCCluster(bank, i);
                if (requireGood && !cluster.isGood()) {
                    continue;
                }
                clusters.add(new LTCCCluster(bank, i));
            }
        }
        return clusters;
    }
    
    public static void save(List<LTCCCluster> clusters, DataEvent event) {
        if (!clusters.isEmpty()) {
            DataBank clusterBank = event.createBank("LTCC::clusters", clusters.size());
            for (LTCCCluster cl : clusters) {
                cl.write(clusterBank);
            }
            event.appendBank(clusterBank);
        }
    }
    
    /**
     * Create a new cluster around a cluster center.
     * 
     * @param center        Central hit.
     * @param clusterid     Unique cluster identifier between 0 and (#clusters -1)
     */
    LTCCCluster(LTCCHit center, int clusterid) {
        this.id = clusterid;
        this.rawPos = new Vector3D();
        add(center);
        center.setClusterCenter(id);
    }
    
    /**
     * Load a cluster from the LTCC::clusters bank.
     * 
     * @param bank  LTCC::clusters bank
     * @param index cluster index
     */
    LTCCCluster(DataBank bank, 
                int index) {
        this.id = bank.getShort("id", index); // should be same as index in most cases
        this.sector = bank.getByte("sector", index);
        this.segment = bank.getShort("segment", index) * this.nphe;
        this.rawPos = new Vector3D(
                bank.getFloat("raw_x", index),
                bank.getFloat("raw_y", index),
                bank.getFloat("raw_z", index));
        this.bestPos = new Vector3D(
                bank.getFloat("x", index),
                bank.getFloat("y", index),
                bank.getFloat("z", index));
        this.dtheta = bank.getFloat("dtheta", index);
        this.dphi = bank.getFloat("dphi", index);
        this.rawHitsLeft = bank.getLong("raw_hits_left", index);
        this.rawHitsRight = bank.getLong("raw_hits_right", index);
        this.nHitsLeft = countHits(rawHitsLeft);
        this.nHitsRight = countHits(rawHitsRight);
        this.nHitsInTime = bank.getShort("intime_nhits", index);
        this.nphe = bank.getFloat("nphe", index);
        this.npheInTime = bank.getFloat("intime_nphe", index);
        this.time = bank.getFloat("time", index);
        this.status = Status.fromInteger(bank.getByte("status", index));
    }

    public void write(DataBank bank) {
        int index = this.id;
        // calculate average position
        Vector3D xyzRaw = this.getRawPosition();
        Vector3D xyz = this.getPosition();
        // save to bank
        bank.setShort("id", index, (short) this.id);
        bank.setByte("status", index, (byte) this.status.code);
        bank.setByte("sector", index, (byte) getSector());
        bank.setShort("segment", index, (short) getSegment());
        bank.setShort("trackid", index, (short) this.trackID);
        bank.setShort("nhits", index, (short) getNHits());
        bank.setShort("intime_nhits", index, (short) getNHitsInTime());
        bank.setFloat("time", index, (float) this.getTime());
        bank.setFloat("nphe", index, (float) getNphe());
        bank.setFloat("intime_nphe", index, (float) getNpheInTime());
        bank.setFloat("x", index, (float) xyz.x());
        bank.setFloat("y", index, (float) xyz.y());
        bank.setFloat("z", index, (float) xyz.z());
        bank.setFloat("raw_x", index, (float)xyzRaw.x());
        bank.setFloat("raw_y", index, (float)xyzRaw.y());
        bank.setFloat("raw_z", index, (float)xyzRaw.z());
        bank.setFloat("dtheta", index, (float) getDtheta());
        bank.setFloat("dphe", index, (float) getDphi());
        bank.setLong("raw_hits_left", index, this.rawHitsLeft);
        bank.setLong("raw_hits_right", index, this.rawHitsRight);
    }

    public void add(LTCCHit hit) {
        // first hit is the cluster center
        if (this.sector < 0) {
            this.sector = hit.getSector();
            hit.setClusterCenter(id);
            updateInTime(hit);
        } else if (Math.abs(hit.getTime()-this.getTime()) < CLUSTER_IN_TIME_CUT) {
            hit.setClusterHit(id);
            updateInTime(hit);
        } else {
            hit.setClusterOutOfTime(id);
        }
        updateRawHits(hit);
        updateTotals(hit);
        updateStatus();
    }
    
        
    public void addTrack(int trackID, Vector3D pos) {
        this.bestPos = pos;
        this.trackID = trackID;
    }   
    
    private void updateRawHits(LTCCHit hit) {
        if (hit.isLeft()) {
            this.rawHitsLeft = encodeHit(
                    this.rawHitsLeft, 
                    hit.getSegment(), 
                    this.nHitsLeft);
            this.nHitsLeft += 1;
        } else {
            this.rawHitsRight = encodeHit(
                    this.rawHitsRight, 
                    hit.getSegment(), 
                    this.nHitsRight);
            this.nHitsLeft += 1;
        }
    }
    
    private void updateTotals(LTCCHit hit) {
        this.nphe += hit.getNphe();
        Vector3D wpos = hit.getPosition().multiply(hit.getNphe());
        this.rawPos.add(wpos);
        this.segment += hit.getSegment() * hit.getNphe();
        this.dtheta += hit.getThetaHalo() * hit.getNphe();
        this.dphi += hit.getPhiHalo() * hit.getNphe();
    }
    private void updateInTime(LTCCHit hit) {
        this.npheInTime += hit.getNphe();
        this.nHitsInTime += 1;
        this.time += hit.getTime() * hit.getNphe();  
    }
    private void updateStatus() {
        if ((this.status == Status.GOOD) 
                && (this.nphe >= GOOD_CLUSTER_NPHE_MIN)
                && (this.nphe <= GOOD_CLUSTER_NPHE_MAX)
                && (this.getNHits() >= GOOD_CLUSTER_NHIT_MIN)
                && (this.getNHits() <= GOOD_CLUSTER_NHIT_MAX)) {
            this.status = Status.GOOD;
        } else {
            this.status = Status.BAD;
        }
    }
    
    public static long encodeHit(long hitMask, int segment, int index) {
        return hitMask | ((long)segment << 8 * (index - 1));
    }
    public static int decodeHit(long hitMask, int index) {
        return (int)(hitMask >> 8 * (index - 1)) & 0xff;
    }
    public static int countHits(long hitMask) {
        int n = 0;
        while (hitMask > 0) {
            hitMask = hitMask >> 8;
            n += 1;
        }
        return n;
    }
    public static List<Integer> decodeHits(long hitMask) {
        List<Integer> hits = new ArrayList<>();
        while (hitMask > 0) {
            int newHit = decodeHit(hitMask, 0);
            hitMask = hitMask >> 8;
            hits.add(newHit);
        }
        return hits;
    }
    
    public int getID() {
        return this.id;
    }
    public int getSector() {
        return this.sector;
    }
    public int getSegment() {
        return (int) (this.segment / this.nphe);
    }
    public double getNphe() {
        return this.nphe;
    }
    public Vector3D getRawPosition() {
        return this.rawPos.multiply(1/this.nphe);
    }
    // bestpos is not weighted
    public Vector3D getPosition() {
        if (bestPos == null) {
            return getRawPosition();
        }
        return this.bestPos;
    }
    public double getTime() {
        return this.time / this.nphe;
    }
    public double getNHits() {
        return this.nHitsLeft + this.nHitsRight;
    }
    public double getDtheta() {
        return this.dtheta / this.nphe;
    }
    public double getDphi() {
        return this.dphi / this.nphe;
    }
    public double getNpheInTime() {
        return this.npheInTime;
    }
    public double getNHitsInTime() {
        return this.nHitsInTime;
    }
    public boolean isGood() {
        return this.status.isGood();
    }    
    public boolean hasTrack() {
        return (this.trackID > 0);
    }
}
