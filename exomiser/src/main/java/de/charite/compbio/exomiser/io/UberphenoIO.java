package de.charite.compbio.exomiser.io;

import hpo.HPOutils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ontologizer.go.OBOParser;
import ontologizer.go.OBOParserException;
import ontologizer.go.Ontology;
import ontologizer.go.Term;
import ontologizer.go.TermContainer;
import sonumina.math.graph.SlimDirectedGraphView;
import de.charite.compbio.exomiser.priority.util.UberphenoAnnotation;
import de.charite.compbio.exomiser.priority.util.UberphenoAnnotationContainer;

/**
 * IO methods for reading the uberpheno obo file and the annotation file.
 * 
 * @author sebastiankohler
 * @version 0.02 (January 15, 2013)
 */
public class UberphenoIO {

	
	/**
	 * This pattern matches an HP, MP, or ZP accession number, e.g., HP:0001234.
	 */
	private final Pattern hpoPattern = Pattern.compile("([HZM]P:\\d{7})");
	
	/**
	 * @param uphenoAnnotationFile
	 * @param uberpheno
	 * @param uberphenoSlim
	 */
	public UberphenoAnnotationContainer createUberphenoAnnotationFromFile(String uphenoAnnotationFile, Ontology uberpheno, SlimDirectedGraphView<Term> uberphenoSlim) {

		UberphenoAnnotationContainer annotations = new UberphenoAnnotationContainer();
		UberphenoIO io = new UberphenoIO();
		try {

			BufferedReader in = new BufferedReader(new FileReader(uphenoAnnotationFile));
			String line = null;
			while ((line = in.readLine()) != null) {
				
				if (line.startsWith("#"))
					continue;
				
				String[] split 		= line.split(";");
				int entrez 			= Integer.parseInt(split[0]);
				String geneSymbol  	= split[1];
				String termidString = split[2]; // this contains uberpheno-term name and ID (ZP,MP,HP)
				
				Matcher mat = hpoPattern.matcher(termidString);
				mat.find();
				String termId = mat.group();
				Term t = uberpheno.getTerm(termId);
				
				HashSet<Integer> omimids = new HashSet<Integer>();
				if (split.length==4){
					omimids = parseOmimsFromLine(split[3]);
				}
				
				UberphenoAnnotation annotation = new UberphenoAnnotation(entrez,geneSymbol,t,omimids);
				addAnnotation(annotation,annotations);
			}
			in.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
		UberphenoAnnotationContainer cleanedUp = cleanUp(annotations, uberphenoSlim, uberpheno.getRootTerm());
		return cleanedUp;
	}
	
	/**
	 * Remove duplicated annotations, i.e. two annotations are referring to terms that are on one
	 * path in the ontology. This will remove the more general annotation and keep only
	 * the most specific annotation.
	 * 
	 * @param annotationContainer
	 * @param uberphenoSlim
	 * @param root
	 * @return an uberpheno annotation container from which duplicate annotations have been removed.
	 */
	private UberphenoAnnotationContainer cleanUp(UberphenoAnnotationContainer annotationContainer, SlimDirectedGraphView<Term> uberphenoSlim, Term root) {
		
		UberphenoAnnotationContainer newContainer = new UberphenoAnnotationContainer();
		
		for (int entrezId : annotationContainer.container.keySet()){
			
			Set<UberphenoAnnotation> annotationSet = annotationContainer.container.get(entrezId);
			ArrayList<Term> annotations = new ArrayList<Term>();
			for (UberphenoAnnotation a : annotationSet)
				annotations.add(a.getTerm());
			
			List<Term> annotationsFiltered = HPOutils.cleanUpAssociation(annotations, uberphenoSlim, root);
			Set<Term> annotationsFilteredHS = new HashSet<Term>(annotationsFiltered);
			
			// rescue the ones with evidence (omim,orpha.. i.e. humans)
			for (UberphenoAnnotation a : annotationSet){
				if (a.getEvidenceOmimIds().size()>0 || annotationsFilteredHS.contains(a.getTerm()))
					addAnnotation(a,newContainer);
			}
		}
		
		return newContainer;
	}
	
	/**
	 * Fetch six-digit omim-ids from a string of the form "ORPHANET:783,OMIM:180849"
	 * @param string
	 * @return set of 6-digit OMIM ids corresponding to string
	 */
	private HashSet<Integer> parseOmimsFromLine(String string) {
		
		HashSet<Integer> omims = new HashSet<Integer>();
		String[] evidences = string.split(","); // ORPHANET:783,OMIM:180849
		for (String ev : evidences){
			String[]id = ev.split(":"); // OMIM:180849
			String db = id[0];
			if (db.equals("OMIM")){
				int db_id = Integer.parseInt(id[1]);
				omims.add(db_id);
			}
		}
		return omims;
	}
	
	/**
	 * Add an annotation to the given annotation container.
	 * @param annotation
	 * @param container
	 */
	private void addAnnotation(UberphenoAnnotation annotation, UberphenoAnnotationContainer container) {
		int entrez = annotation.getEntrezGeneId();
		HashSet<UberphenoAnnotation> annotationsOfGene;
		if (container.container.containsKey(entrez))
			annotationsOfGene = container.container.get(entrez);
		else
			annotationsOfGene = new HashSet<UberphenoAnnotation>();
		
		annotationsOfGene.add(annotation);
		container.container.put(entrez, annotationsOfGene);
	}

	
	/**
	 * Create a randomized version of the associations between genes and uberpheno-terms.
	 * @param uberphenoAnnotationContainer
	 * @param allUberphenoTermsHpMpZp
	 * @return a randomized version of gene/uberpheno term associations
	 */
    @SuppressWarnings (value="unchecked")
	public UberphenoAnnotationContainer createRandomizedVersion(UberphenoAnnotationContainer uberphenoAnnotationContainer, List<Term> allUberphenoTermsHpMpZp) {
		
		UberphenoAnnotationContainer annotations = new UberphenoAnnotationContainer();
		
		Collections.shuffle(allUberphenoTermsHpMpZp);
		Random generator = new Random();
		generator.setSeed(System.currentTimeMillis());
		
		for (int geneId : uberphenoAnnotationContainer.container.keySet()){
			
			for (UberphenoAnnotation oldAnnotation : uberphenoAnnotationContainer.container.get(geneId)){
				
				HashSet<Integer> omims = new HashSet<Integer>();
				HashSet<Integer> old = ((HashSet<Integer>) oldAnnotation.getEvidenceOmimIds().clone());
				omims.addAll(old);
				
				int randomIndex = generator.nextInt(allUberphenoTermsHpMpZp.size());
				UberphenoAnnotation newAnnotation = new UberphenoAnnotation(geneId,oldAnnotation.getGeneSymbol(),allUberphenoTermsHpMpZp.get(randomIndex),omims);
				addAnnotation(newAnnotation,annotations);
			}
			
		}
		return annotations;
	}
	

	/**
	 * Parse the uberpheno obo-file.
	 * @param uphenoFile
	 * @return an Ontology object corresponding to the uberpheno file.
	 */
	public Ontology parseUberpheno(String uphenoFile) {
		// parse uberpheno ontology
		OBOParser oboParser = new OBOParser(uphenoFile);
		try
		{
			System.out.println(oboParser.doParse());
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (OBOParserException e) {
			e.printStackTrace();
		}
		TermContainer termContainer 	= new TermContainer(oboParser.getTermMap(),oboParser.getFormatVersion(),oboParser.getDate());
		return new Ontology(termContainer);
	}
	
}
