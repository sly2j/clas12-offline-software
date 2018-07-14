/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.rec.ltcc;

import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataBank;

import java.util.LinkedList;
import java.util.List;


/**
 * Raw LTCC ADC/TDC info
 * 
 * @author sly2j
 */
public class LTCCRawHit {

    
    /* raw LTCC ADC/TDC info */
    private final int sector;
    private final int layer;
    private final int component;
    private final int order;
    private final int ADC;
    private final float time;
    private final int ped;
    private final int TDC;
        
    
    /**
     * 
     * Load all the raw LTCC hits for this event.
     * 
     * Note: Optionally also loads the LTCC::tdc bank if available.
     * 
     * @param event   Input event stream
     * @return          List of raw LTCC hits
     */
    public static List<LTCCRawHit> load(DataEvent event) {
        List<LTCCRawHit> rawHits = new LinkedList<>();
        if (event.hasBank("LTCC::adc")) {
            DataBank adcBank = event.getBank("LTCC::adc");
            DataBank tdcBank = null;
            if (event.hasBank("LTCC::tdc")) {
                tdcBank = event.getBank("LTCC::tdc");
            }
            for (int i = 0; i < adcBank.rows(); ++i) {
                LTCCRawHit rawHit = new LTCCRawHit(adcBank, tdcBank, i);
                rawHits.add(rawHit);
            }
        }
        return rawHits;
    }
    
    /**
     * 
     * Construct a LTCCRawHit from the ADC info
     * Optionally add TDC info if tdcBank is not null
     *
     * 
     * @param adcBank   bank    LTCC::adc bank
     * @param tdcBank   bank    LTCC::tdc bank, can be null
     * @param index     bank    hit index
     */
    LTCCRawHit(DataBank adcBank,
               DataBank tdcBank,
               int index) {
        this.sector = adcBank.getByte("sector", index);
        this.layer = adcBank.getByte("layer", index);
        this.component = adcBank.getShort("component", index);
        this.order = adcBank.getByte("order", index);
        this.ADC = adcBank.getInt("ADC", index);
        this.time = adcBank.getFloat("time", index);
        this.ped = adcBank.getShort("ped", index);
        if (tdcBank != null) {
            this.TDC = tdcBank.getInt("TDC", index);
        } else {
            this.TDC = -1;
        }
    }
        
    /**
     * 
     * Construct a LTCCRawHit from the ADC info (ignoring TDC info).
     * 
     * @param adcBank   bank    LTCC::adc bank
     * @param index     bank    hit index
     */
    LTCCRawHit(DataBank adcBank, 
               int index) {
        this(adcBank, null, index); 
    }
    
    /**
     * @return the sector
     */
    public int getSector() {
        return sector;
    }

    /**
     * @return the layer
     */
    public int getLayer() {
        return layer;
    }

    /**
     * @return the component
     */
    public int getComponent() {
        return component;
    }

    /**
     * @return the order
     */
    public int getOrder() {
        return order;
    }

    /**
     * @return the ADC
     */
    public int getADC() {
        return ADC;
    }

    /**
     * @return the time
     */
    public float getTime() {
        return time;
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
    
}
