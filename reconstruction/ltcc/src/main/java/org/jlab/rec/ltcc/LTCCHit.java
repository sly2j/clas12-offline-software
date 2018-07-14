/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.rec.ltcc;

import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.geom.prim.Vector3D;

import java.util.LinkedList;
import java.util.List;

/**
 * Calibrated LTCC Hit info
 * 
 * @author sly2j
 */
public class LTCCHit {

    /* nphe requirements for a good hit */
    static private final double NPHE_MIN_HIT = 0;
    static private final double NPHE_MAX_HIT = 10000.;
    /* Default calibration constants
     * Note: SPE sigma is set to -1 to indicate we are not using a CCDB value.
    */
    static private final double DEFAULT_SPE_MEAN = 200.;
    static private final double DEFAULT_SPE_SIGMA = -1.; 
    /* 
     * THETA/PHI "halo" factors -> multipliers to the elliptical mirror width 
     * to account for the possible mirror -> PMT skip that happens for tracks
     * coming in at non-trivial angles (this is the normal behavior!)
    */
    static private final double THETA_HALO_FACTOR = 5.;
    static private final double PHI_HALO_FACTOR = 1.;
        
    /* LTCC specs
     * polar coordinates (in lab frame) of the eliptical mirror centers 
     * for each of the segments for from target for sector 1/right
     * note that 1/left  has the same coordinates, but with -1. * phi
    */
    static private final double[] RHO0 = { // [cm]
        664.612, 661.408, 657.519, 654.148, 650.091, 645.757,
        641.874, 638.225, 635.421, 632.638, 631.206, 627.403,
        626.36, 623.957, 617.503, 621.91, 621.845, 623.926};
    static private final double[] THETA0 = { // [degree]
        5.76294, 7.06809, 8.38733, 9.73387, 11.0776, 12.4349,
        13.8235, 15.2304, 16.6625, 18.1621, 19.7188, 21.3341,
        22.9771, 24.873, 26.7458, 28.586, 30.5954, 31.175};
    static private final double[] PHI0 = { // [degree]
        3.72443, 6.45884, 8.4087, 9.7843, 10.8255, 11.6715,
        12.3397, 12.6632, 12.9966, 13.2521, 13.3003, 13.4214,
        11.7866, 12.0826, 12.3421, 12.3184, 12.2741, 12.7263};
    
    /* Hit status code */
    private enum Status {
        BAD (-1),
        GOOD (0),  // good track without cluster assigment (orphan)
        CENTER (1),
        PERIPHERY (2),
        OUT_OF_TIME (3);
        
        private final int status;
        Status(final int s) {
            status = s;
        }
        private int getValue() {
            return status;
        }
        private Boolean isGood() {
            return status >= 0;
        }
        private static Status fromInteger(int s) {
            switch (s) {
                case 3:
                    return OUT_OF_TIME;
                case 2:
                    return PERIPHERY;
                case 1:
                    return CENTER;
                case 0:
                    return GOOD;
                default:
                    return BAD;
            }
        }
    }
    
    /* Hit position */
    private final int sector;       // LTCC sector
    private final int side;         // LTCC side (1: left, 2: right)
    private final int segment;      // LTCC segment (1 -> 18)
    
    /* raw info from RawLTCCHit */
    private final int ADC;          // integrated ADC
    private final int ped;          // Pedestal
    private final int TDC;          // TDC info

    /* calibration info */
    private final double speMean;    // Mean from CCDB
    private final double speSigma;   // Sigma from CCDB
    private final double hv;         // HV from CCDB (currently unused)
    private final double tet;        // tet from CCDB (currently unused)
    
  
    /* calibrated quantities */
    private final double time;       // time (currently not calibrated!)
    private final double nphe;       // Number of photo-electrons
    
    /* Hit status */
    private Status status;
    
    /* associated cluster id */
    private int clusterid = -1;    // (-1 if none)
    
    /* internal phi index starting from [sector 1/left, ... , sector 6/right]
     * Note that this index differs from the usual convention that goes from
     * [sector 1/right -> ... -> sector 1/left]
     * see also getLTCCPhiIndex() vs getPhiIndex() */
    private final int LTCCPhiIndex;
    
    
    /**
     * Load calibrated hits from the event stream.
     * 
     * @param event Event with LTCC::hits field
     * @return List of calibrated LTCC hits
     */
    public static List<LTCCHit> load(DataEvent event) {
        List<LTCCHit> hits= new LinkedList<>();
        if (event.hasBank("LTCC::hits")) {
            DataBank hitBank = event.getBank("LTCC::hits");
            for (int i = 0; i < hitBank.rows(); ++i) {
                hits.add(new LTCCHit(hitBank, i));
            }
        }
        return hits;
    }
    
    /**
     * 
     * Calibrate raw hits using info from CCDB for this run.
     * 
     * Will use default calibration value if no CCDB available (MC).
     * 
     * @param rawHits   Raw un-calibrated hits
     * @param ccdb      Handle to CCDB     
     * @param run       Current run number
     * @return List of calibrated LTCC hits
     */
    public static List<LTCCHit> load(List<LTCCRawHit> rawHits, 
                                     ConstantsManager ccdb,
                                     int run) {
        IndexedTable speDB = null;
        IndexedTable timingDB = null;
        if (ccdb != null && run > 0) {
            speDB = ccdb.getConstants(run, "/calibration/ltcc/spe");
            //timing_offset = ccdb.getConstants(run, "/calibration/ltcc/timing_offset");
        }
        List<LTCCHit> hits = new LinkedList<>();
        for (LTCCRawHit raw : rawHits) {
            LTCCHit hit = new LTCCHit(raw, speDB, timingDB);
            if (hit.isGood()) {
                hits.add(hit);
            }
        }
        return hits; 
    }
    /**
     * Save calibrated hits to the data stream.
     * 
     * @param hits      List of calibrated LTCC hits
     * @param event     Output data stream. 
     */
    public static void save(List<LTCCHit> hits, DataEvent event) {
        if (!hits.isEmpty()) {
            DataBank hitBank = event.createBank("LTCC:hits", hits.size());
            for (int i = 0; i < hits.size(); ++i ) {
                hits.get(i).write(hitBank, i);
            }
            event.appendBank(hitBank);
        }
    }
    
    /**
     * 
     * Load a calibrated hit from the LTCC::hits bank.
     * 
     * @param hitBank   bank    LTCC::hits bank
     * @param index     int     hit index
     */
    LTCCHit(DataBank hitBank,
            int index) {
        this.sector = hitBank.getByte("sector", index);
        this.side = hitBank.getByte("side", index);
        this.segment = hitBank.getShort("segment", index);
        this.time = hitBank.getFloat("time", index);
        this.nphe = hitBank.getFloat("nphe", index);
        this.status = Status.fromInteger(hitBank.getByte("status", index));
        this.clusterid = hitBank.getShort("clusterid", index);
        this.ADC = hitBank.getInt("ADC", index);
        this.ped = hitBank.getShort("ped", index);
        this.TDC = hitBank.getInt("TDC", index);
        this.speMean = hitBank.getFloat("spe_mean", index);
        this.speSigma = hitBank.getFloat("spe_sigma", index);
        this.hv = hitBank.getFloat("hv", index);
        this.tet = hitBank.getFloat("tet", index);
        this.LTCCPhiIndex = calcLTCCPhiIndex();
    }

    /**
     * Created a calibrated hit from a raw LTCC hit.
     * 
     * @param raw       Raw LTCC hit
     * @param speDB     SPE Calibration DB, fallback to default if null
     * @param timingDB  Timing DB, currently unused.
     */
    LTCCHit(LTCCRawHit raw,
            IndexedTable speDB,
            IndexedTable timingDB) {
        this.sector = raw.getSector();
        this.side = raw.getOrder() + 1;
        this.segment = raw.getComponent();
        this.ADC = raw.getADC();
        this.ped = raw.getPed();
        this.TDC = raw.getTDC();
        // sane defaults for the calibration constants, useful for MC
        double mean = DEFAULT_SPE_MEAN;
        double sigma = DEFAULT_SPE_SIGMA;
        if (speDB != null) {
            double meanCCDB = speDB.getDoubleValue("mean", sector, side, segment);
            double sigmaCCDB = speDB.getDoubleValue("sigma", sector, side, segment);
            // sane default in case of no entry in ccdb, should never happen
            if (meanCCDB > 0) {
                mean = meanCCDB;
                sigma = sigmaCCDB;
            }
        }
        this.speMean = mean;
        this.speSigma = sigma;
        this.hv = 0; // Not yet in CCDB
        this.tet = 0; // Not yet in CCDB
        this.time = raw.getTime(); // no time calibration 
        this.nphe = calcNphe();
        this.status = calcStatus();
        this.LTCCPhiIndex = calcLTCCPhiIndex();
    }

    /**
     *
     * Write this hit to a databank.
     * 
     * @param bank      Bank to write to.
     * @param index     Index for this hit.
     */
    public void write(DataBank bank, int index) {
        bank.setShort("id", index, (short) index);
        bank.setByte("sector", index, (byte) this.sector);
        bank.setByte("side", index, (byte) this.side);
        bank.setShort("segment", index, (short) this.segment);
        bank.setFloat("time", index, (float) this.time);
        bank.setFloat("nphe", index, (float) this.nphe);
        bank.setByte("status", index, (byte) this.status.getValue());
        bank.setShort("clusterid", index, (short) this.clusterid);
        bank.setInt("ADC", index, (int) this.ADC);
        bank.setShort("ped", index, (short) this.ped);
        bank.setInt("TDC", index, (int) this.TDC);
        bank.setFloat("speMean", index, (float) this.speMean);
        bank.setFloat("speSigma", index, (float) this.speSigma);
        bank.setFloat("hv", index, (float) this.hv);
        bank.setFloat("tet", index, (float) this.tet);
    }

    /* Calculate the calibrated number of photo-electrons. */
    private double calcNphe() {
        return (this.getADC() > 0 ? this.getADC() / this.getSpeMean() : -1.);
    }
    /* Calculate the LTCC Phi index (differs from the regular phi index,
     * starts counting [sector 1/left -> ... -> sector 6/right]
    */
    private int calcLTCCPhiIndex() {
        return 2 * (this.getSector() - 1) + (this.getSide() - 1);
    }
    /* Calculate the initial status (good or bad) */
    private Status calcStatus() {
        return (this.getADC() >= 0 && this.getNphe() > NPHE_MIN_HIT && this.getNphe() < NPHE_MAX_HIT)
                ? Status.GOOD
                : Status.BAD;
    }
    /**
     * Is this a good hit?
     * @return Boolean status
     */
    public Boolean isGood() {
        return status.isGood();
    }
    
    /**
     * Assign this hit as the cluster center for cluster with id.
     * @param id    associated cluster ID
     */
    public void setClusterCenter(int id) {
        assignToCluster(id, Status.CENTER);
    }   
    
    /**
     * Assign this hit to a cluster with id.
     * @param id    associated cluster ID
     */
    public void setClusterHit(int id) {
        assignToCluster(id, Status.PERIPHERY);
    }
    /**
     * Assign this hit to a cluster with id, marked as out-of-time.
     * @param id    associated cluster ID
     */
    public void setClusterOutOfTime(int id) {
        assignToCluster(id, Status.OUT_OF_TIME);
    }   
    
    /**
     * Assign this hit to a cluster with id.
     * 
     * Status of the hit in the cluster can be:
     *  - CENTER
     *  - PERIPHERY (default)
     *  - OUT_OF_TIME
     * 
     * @param id    associated cluster ID
     * @param updatedStatus Status of this hit in the cluster
     */
    private void assignToCluster(int id,
                                 Status updatedStatus) {
        this.clusterid = id;
        this.status = updatedStatus;
    }
    
    /**
     * 
     * Is this hit a neighbor within a theta and timing window?.
     * 
     * Note: Phi is dealt with by enforcing both hits to be in the same sector.
     * 
     * @param hit       Hit to compare to
     * @param dThetaMax Max segment ("theta") difference
     * @param dTimeMax  Max time difference
     * @return Boolean
     */
    public boolean isNeighbor(LTCCHit hit, int dThetaMax, double dTimeMax) {
        int dTheta = Math.abs(this.getSegment() - hit.getSegment());
        double dTime = Math.abs(this.getTime() - hit.getTime());
        return (dTheta <= dThetaMax && this.getSector() == hit.getSector() && dTime <= dTimeMax);
    }
    /**
     * 
     * Is this hit a neighbor within a timing window?.
     * 
     * Note: Phi is dealt with by enforcing both hits to be in the same sector.
     * Note: Default version only considers neighboring segments.
     * 
     * @param hit
     * @param dTimeMax
     * @return 
     */
    public boolean isNeighbor(LTCCHit hit, double dTimeMax) { 
        return isNeighbor(hit, 1, dTimeMax);
    }
    /**
     * 
     * Hit from left side of sector?
     * 
     * @return 
     */
    public boolean isLeft() {
        return (this.side == 1);
    }
    /**
     * 
     * Hit from right side of sector?
     * 
     * @return 
     */
    public boolean isRight() {
        return !isLeft();
    }
    
    /**
     * 
     * Get the center position of the elliptical mirror closest to the struck PMT.
     * 
     * Note: This is most often not the actual mirror that was struck! 
     * 
     * @return Point3D with the mirror center.
     */
    public Vector3D getPosition() {
        double phi = Math.toRadians(PHI0[this.segment - 1] 
                * (this.side == 1 ? -1 : 1))
                + 2. * Math.PI * (this.sector - 1) / 6.;
        Vector3D v = Vector3D.fromSpherical(
                RHO0[this.segment - 1], 
                Math.toRadians(THETA0[this.segment - 1]),
                phi);
        return v;
    }

    /**
     * 
     * Get the theta "halo" of possible tracks associated with this hit. 
     * Equal to THETA_HALO_FACTOR x the the angular height of the elliptical mirror.
     * 
     * @return theta halo in radians
     */
    public double getThetaHalo() {
        /*
         * Estimate theta as the average distance between segment-1 and segment+2.
         * Handle edges by doing (segment -> segment +/-1)
        */
        double angleDeg;
        switch (this.segment) {
            case 0:
                angleDeg = THETA0[1] - THETA0[2];
                break;
            case 18:
                angleDeg = THETA0[18] - THETA0[17];
                break;
            default:
                angleDeg = .5 * (THETA0[this.segment + 1] - THETA0[this.segment - 1]);
                break;
        }
        return THETA_HALO_FACTOR * Math.toRadians(angleDeg);
    }
    /**
     * Get the phi "halo" of possible tracks associated with this hit.
     * Equal to PHI_HALO_FACTOR x the angular width of the elliptical mirror.
     * 
     * @return phi halo in radians 
     */
    public double getPhiHalo() {
        /* Phi width is equal to the distance to the mirror center at the
         * other side (between -phi -> phi, hence 2*phi)
        */
        return PHI_HALO_FACTOR * 2.*Math.toRadians(PHI0[this.segment - 1]);
    }
    
    /**
     * Get the usual CLAS12 phi index.
     * 
     * Goes from [sector 1/right -> ... -> sector 1/left]
     * 
     * @return phi index
     */
    public int getPhiIndex() {
        return getPhiIndex(this.LTCCPhiIndex);
    }
    /**
     * Static function to return the usual CLAS12 phi index
     * 
     * Goes from [sector 1/right -> ... -> sector 1/left]
     * 
     * @param ltccPhiIndex the internal LTCC phi index
     * @return phi index
     */
    static public int getPhiIndex(int ltccPhiIndex) {
        return (12 + ltccPhiIndex - 1) % 12;
    }    
  
    /**
     * @return the sector
     */
    public int getSector() {
        return sector;
    }

    /**
     * @return the side
     */
    public int getSide() {
        return side;
    }

    /**
     * @return the segment
     */
    public int getSegment() {
        return segment;
    }

    /**
     * @return the ADC
     */
    public int getADC() {
        return ADC;
    }

    /**
     * @return the ped
     */
    public int getPed() {
        return ped;
    }

    /**
     * @return the TDC
     */
    public int getTDC() {
        return TDC;
    }

    /**
     * @return the speMean
     */
    public double getSpeMean() {
        return speMean;
    }

    /**
     * @return the speSigma
     */
    public double getSpeSigma() {
        return speSigma;
    }

    /**
     * @return the hv
     */
    public double getHv() {
        return hv;
    }

    /**
     * @return the tet
     */
    public double getTet() {
        return tet;
    }

    /**
     * @return the time
     */
    public double getTime() {
        return time;
    }

    /**
     * @return the nphe
     */
    public double getNphe() {
        return nphe;
    }

    /**
     * @return the clusterid
     */
    public int getClusterid() {
        return clusterid;
    }

    /**
     * 
     * Internal LTCC phi index.
     * 
     * Runs from  [sector 1/left -> ... -> sector 6/right]
     * 
     * @return the LTCCPhiIndex
     */
    public int getLTCCPhiIndex() {
        return LTCCPhiIndex;
    }
}
