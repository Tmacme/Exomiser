/*
 * The Exomiser - A tool to annotate and prioritize variants
 *
 * Copyright (C) 2012 - 2016  Charite Universitätsmedizin Berlin and Genome Research Ltd.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package org.monarchinitiative.exomiser.core.factories;

import de.charite.compbio.jannovar.annotation.VariantEffect;
import org.monarchinitiative.exomiser.core.dao.*;
import org.monarchinitiative.exomiser.core.model.RegulatoryFeature;
import org.monarchinitiative.exomiser.core.model.TopologicalDomain;
import org.monarchinitiative.exomiser.core.model.Variant;
import org.monarchinitiative.exomiser.core.model.VariantData;
import org.monarchinitiative.exomiser.core.model.frequency.Frequency;
import org.monarchinitiative.exomiser.core.model.frequency.FrequencyData;
import org.monarchinitiative.exomiser.core.model.frequency.FrequencySource;
import org.monarchinitiative.exomiser.core.model.pathogenicity.PathogenicityData;
import org.monarchinitiative.exomiser.core.model.pathogenicity.PathogenicityScore;
import org.monarchinitiative.exomiser.core.model.pathogenicity.PathogenicitySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
@Service
public class VariantDataServiceImpl implements VariantDataService {

    private static final Logger logger = LoggerFactory.getLogger(VariantDataServiceImpl.class);

    @Autowired
    private VariantDataDaoTabix variantDataDao;
    @Autowired
    private FrequencyDao frequencyDao;
    @Autowired
    private PathogenicityDao pathogenicityDao;
    @Resource(name = "caddDao")
    private CaddDao caddDao;
    @Resource(name = "remmDao")
    private RemmDao remmDao;
    @Autowired
    private RegulatoryFeatureDao regulatoryFeatureDao;
    @Autowired
    private TadDao tadDao;

    @Override
    public VariantData getVariantData(Variant variant, Set<FrequencySource> frequencySources, Set<PathogenicitySource> pathogenicitySources) {
        VariantData variantData1 = variantDataDao.getVariantData(variant);

        List<PathogenicityScore> allPathScores = new ArrayList<>();
        final VariantEffect variantEffect = variant.getVariantEffect();
        //Polyphen, Mutation Taster and SIFT are all trained on missense variants - this is what is contained in the original variant table, but we shouldn't know that.
        if (variantEffect == VariantEffect.MISSENSE_VARIANT) {
            PathogenicityData missenseScores = variantData1.getPathogenicityData();
            allPathScores.addAll(missenseScores.getPredictedPathogenicityScores());
        } else if (pathogenicitySources.contains(PathogenicitySource.REMM) && variant.isNonCodingVariant()) {
            //REMM is trained on non-coding regulatory bits of the genome, this outperforms CADD for non-coding variants
            PathogenicityData nonCodingScore = remmDao.getPathogenicityData(variant);
            allPathScores.addAll(nonCodingScore.getPredictedPathogenicityScores());
        }
        //CADD does all of it although is not as good as REMM for the non-coding regions.
        if (pathogenicitySources.contains(PathogenicitySource.CADD)) {
            PathogenicityData caddScore = caddDao.getPathogenicityData(variant);
            allPathScores.addAll(caddScore.getPredictedPathogenicityScores());
        }

        PathogenicityData pathogenicityData = pathDataFromSpecifiedDataSources(allPathScores, pathogenicitySources);

        FrequencyData frequencyData = frequencyDataFromSpecifiedSources(variantData1.getFrequencyData(), frequencySources);

        return new VariantData(frequencyData, pathogenicityData);
    }

    @Override
    public FrequencyData getVariantFrequencyData(Variant variant, Set<FrequencySource> frequencySources) {
        FrequencyData allFrequencyData = frequencyDao.getFrequencyData(variant);
        return frequencyDataFromSpecifiedSources(allFrequencyData, frequencySources);
    }

    protected FrequencyData frequencyDataFromSpecifiedSources(FrequencyData allFrequencyData, Set<FrequencySource> frequencySources) {
        List<Frequency> frequencies = allFrequencyData.getKnownFrequencies();
        Set<Frequency> wanted = new HashSet<>();
        for (int i = 0; i < frequencies.size(); i++) {
            Frequency frequency = frequencies.get(i);
            if (frequencySources.contains(frequency.getSource())) {
                wanted.add(frequency);
            }
        }
        if (allFrequencyData.getRsId() == null && wanted.isEmpty()) {
            return FrequencyData.EMPTY_DATA;
        }
        return new FrequencyData(allFrequencyData.getRsId(), wanted);
    }

    @Override
    public PathogenicityData getVariantPathogenicityData(Variant variant, Set<PathogenicitySource> pathogenicitySources) {
        //OK, this is a bit stupid, but if no sources are defined we're not going to bother checking for data
        if (pathogenicitySources.isEmpty()) {
            return PathogenicityData.EMPTY_DATA;
        }

        List<PathogenicityScore> allPathScores = new ArrayList<>();
//        final VariantEffect variantEffect = variant.getVariantEffect();
//        //Polyphen, Mutation Taster and SIFT are all trained on missense variants - this is what is contained in the original variant table, but we shouldn't know that.
//        if (variantEffect == VariantEffect.MISSENSE_VARIANT) {
//            PathogenicityData missenseScores = pathogenicityDao.getPathogenicityData(variant);
//            allPathScores.addAll(missenseScores.getPredictedPathogenicityScores());
//        }
//        else
        if (pathogenicitySources.contains(PathogenicitySource.REMM) && variant.isNonCodingVariant()) {
            //REMM is trained on non-coding regulatory bits of the genome, this outperforms CADD for non-coding variants
            PathogenicityData nonCodingScore = remmDao.getPathogenicityData(variant);
            allPathScores.addAll(nonCodingScore.getPredictedPathogenicityScores());
        }

        //CADD does all of it although is not as good as REMM for the non-coding regions.
        if (pathogenicitySources.contains(PathogenicitySource.CADD)) {
            PathogenicityData caddScore = caddDao.getPathogenicityData(variant);
            allPathScores.addAll(caddScore.getPredictedPathogenicityScores());
        }

        return pathDataFromSpecifiedDataSources(allPathScores, pathogenicitySources);
    }

    protected PathogenicityData pathDataFromSpecifiedDataSources(List<PathogenicityScore> allPathScores, Set<PathogenicitySource> pathogenicitySources) {
        Set<PathogenicityScore> wanted = new HashSet<>();
        for (int i = 0; i < allPathScores.size(); i++) {
            PathogenicityScore score = allPathScores.get(i);
            if (pathogenicitySources.contains(score.getSource())) {
                wanted.add(score);
            }
        }
        if (wanted.isEmpty()) {
            return PathogenicityData.EMPTY_DATA;
        }
        return new PathogenicityData(wanted);
    }

    @Override
    public List<RegulatoryFeature> getRegulatoryFeatures() {
        return regulatoryFeatureDao.getRegulatoryFeatures();
    }

    @Override
    public List<TopologicalDomain> getTopologicallyAssociatedDomains() {
        return tadDao.getAllTads();
    }

}
