package fr.certu.chouette.struts;

import amivif.schema.RespPTLineStructTimetable;
import chouette.schema.ChouettePTNetworkType;
import fr.certu.chouette.echange.ILectureEchange;
import fr.certu.chouette.modele.PositionGeographique;
import fr.certu.chouette.modele.TableauMarche;
import fr.certu.chouette.service.amivif.IAmivifAdapter;
import fr.certu.chouette.service.amivif.ILecteurAmivifXML;
import fr.certu.chouette.service.commun.CodeIncident;
import fr.certu.chouette.service.commun.ServiceException;
import fr.certu.chouette.service.fichier.IImportateur;
import fr.certu.chouette.service.identification.IIdentificationManager;
import fr.certu.chouette.service.importateur.IImportCorrespondances;
import fr.certu.chouette.service.importateur.IReducteur;
import fr.certu.chouette.service.importateur.monoitineraire.csv.IImportHorairesManager;
import fr.certu.chouette.service.importateur.monoitineraire.csv.impl.LecteurCSV;
import fr.certu.chouette.service.importateur.monoligne.ILecteurCSV;
import fr.certu.chouette.service.importateur.multilignes.ILecteurPrincipal;
import fr.certu.chouette.service.validation.commun.TypeInvalidite;
import fr.certu.chouette.service.xml.ILecteurEchangeXML;
import fr.certu.chouette.service.xml.ILecteurFichierXML;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Logger;
import org.springframework.util.FileCopyUtils;

@SuppressWarnings("serial")
public class ImportAction extends GeneriqueAction {
	
	private static final Log                    logger                 = LogFactory.getLog(ImportAction.class);
	private static final String					SUCCESS_ITINERAIRE	   = "success_itineraire";
	private static final String					INPUT_ITINERAIRE	   = "input_itineraire";
	private static final Logger                 log                    = Logger.getLogger(ImportAction.class);
	private              String                 fichierContentType;
	private              File                   fichier;
	private              boolean                incremental;
	private              String                 fichierFileName;
	private              ILecteurCSV            lecteurCSV;
	private              ILecteurPrincipal      lecteurCSVPrincipal;
	private              ILecteurPrincipal      lecteurCSVHastus;
	private              ILecteurPrincipal      lecteurCSVPegase;
	private              ILecteurPrincipal      lecteurXMLAltibus;
	private              ILecteurEchangeXML     lecteurEchangeXML;
	private              ILecteurFichierXML     lecteurFichierXML;	
	private              IImportateur           importateur            = null;
	private              IAmivifAdapter         amivifAdapter;
	private              ILecteurAmivifXML      lecteurAmivifXML;	
	private              String                 useAmivif;
	private              String                 useCSVGeneric;
	private              String                 useHastus;
	private              String                 useAltibus;
	private              String                 usePegase;
	private              IIdentificationManager identificationManager;
	private				 IImportHorairesManager importHorairesManager;
	private              IImportCorrespondances importCorrespondances;
	private				 Long					idLigne;
	private              String                 logFileName;
	private              IReducteur             reducteur;
	private              String                 baseName;
	
	public ImportAction() {
		super();
	}
	
	@Override
	public String execute() throws Exception {
		return SUCCESS;
	}
	
	public String reduireHastus() {
		String canonicalPath = null;
		try {
			canonicalPath = fichier.getCanonicalPath();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		String newCanonicalPath = reducteur.reduire(canonicalPath, true);
		addActionMessage("Reduction des données HASTUS effectu&eacute;e. R&eacute;sultat da la reduction dans le fichier : "+newCanonicalPath);
		return SUCCESS;
	}
	
	public String analyseHastus() {
		String canonicalPath = null;
		try {
			canonicalPath = fichier.getCanonicalPath();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		String newCanonicalPath = reducteur.reduire(canonicalPath, true);
		try {
			lecteurCSVHastus.lireCheminFichier(newCanonicalPath);
		}
		catch (ServiceException e) {
			if (CodeIncident.ERR_CSV_NON_TROUVE.equals(e.getCode())) {
				String message = getText("import.csv.fichier.introuvable");
				message += e.getMessage();
				addActionError(message);
			}
			else {
				String defaut = "defaut";
				List<String> args = new ArrayList<String>();
				args.add("aaa");
				args.add("bbb");
				args.add("ccc");
				String message = getText("import.csv.format.ko", args.toArray(new String[0]));
				message += e.getMessage();
				addActionError(message);
			}
			return INPUT;
		}
		List<ILectureEchange> lecturesEchange = lecteurCSVHastus.getLecturesEchange();
		addActionMessage("Validation des données HASTUS effectu&eacute;e. R&eacute;sultat da la validation dans le fichier de log : "+logFileName);
		return SUCCESS;
	}
	
	public String importAltibus() {
		try {
			lecteurXMLAltibus.lireCheminFichier(null);
		}
		catch(Throwable e) {
			addActionError(e.getMessage());
			return INPUT;
		}
		addActionMessage("Cr&eacute;ation des lignes en base effectu&eacute;e");
		return SUCCESS;
	}
	
	public String importPegase() {
		String canonicalPath = null;
		try {
			canonicalPath = fichier.getCanonicalPath();
			log.debug("IMPORT DU FICHIER PEGASE : "+canonicalPath);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		try {
			lecteurCSVPegase.lireCheminFichier(canonicalPath);
		}
		catch(Throwable e) {
			addActionError(e.getMessage());
			return INPUT;
		}
		boolean echec = false;
		List<ILectureEchange> lecturesEchange = lecteurCSVPegase.getLecturesEchange();
		for (ILectureEchange lectureEchange : lecturesEchange) {
			try {
				importateur.importer(false, lectureEchange, false);
				addActionMessage("La ligne "+lectureEchange.getLigneRegistration()+" a été import&eacute;e.");
			}
			catch(Exception e) {
				echec = true;
				this.addFieldError("fieldName_1", "errorMessage 1");
				this.addFieldError("fieldName_2", "errorMessage 2");
				addActionError("La ligne "+lectureEchange.getLigneRegistration()+" ne s'est pas import&eacute;e : "+e.getMessage());
				log.error("Impossible de créer la ligne en base, msg = " + e.getMessage(), e);
			}
		}
		if (echec)
			return INPUT;
		addActionMessage("Toutes les lignes ont été import&eacute;es avec succ&eacute;s.");
		return SUCCESS;
	}
	
	public String importHastus() {
		String canonicalPath = null;
		try {
			canonicalPath = fichier.getCanonicalPath();
			if (incremental)
				log.debug("IMPORT INCREMENTAL DU FICHIER : "+canonicalPath);
			else
				log.debug("IMPORT DU FICHIER : "+canonicalPath);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		String newCanonicalPath = reducteur.reduire(canonicalPath, true);
		log.debug("DECOMPRESSION VERS LE FICHIER : "+newCanonicalPath);
		try {
			lecteurCSVHastus.lireCheminFichier(newCanonicalPath);
		}
		catch (ServiceException e) {
			if (CodeIncident.ERR_CSV_NON_TROUVE.equals(e.getCode())) {
				String message = getText("import.csv.fichier.introuvable");
				message += e.getMessage();
				addActionError(message);
			}
			else {
				List<String> args = new ArrayList<String>();
				args.add("aaa");
				args.add("bbb");
				args.add("ccc");
				String message = getText("import.csv.format.ko", args.toArray(new String[0]));
				message += e.getMessage();
				addActionError(message);
			}
			return INPUT;
		}
		boolean echec = false;
		List<ILectureEchange> lecturesEchange = lecteurCSVHastus.getLecturesEchange();
		for (ILectureEchange lectureEchange : lecturesEchange) {
			try {
				if (!lectureEchange.getLigneRegistration().equals("SP") &&
						!lectureEchange.getLigneRegistration().equals("88") &&
						!lectureEchange.getLigneRegistration().equals("NAVL")) {
					importateur.importer(false, lectureEchange, incremental);
					addActionMessage("La ligne "+lectureEchange.getLigneRegistration()+" a été import&eacute;e.");
				}
			}
			catch(Exception e) {
				echec = true;
				this.addFieldError("fieldName_1", "errorMessage 1");
				this.addFieldError("fieldName_2", "errorMessage 2");
				addActionError("La ligne "+lectureEchange.getLigneRegistration()+" ne s'est pas import&eacute;e : "+e.getMessage());
				log.error("Impossible de créer la ligne en base, msg = " + e.getMessage(), e);
			}
		}
		if (echec)
			return INPUT;
		addActionMessage("Toutes les lignes ont été import&eacute;es avec succ&eacute;s.");
		return SUCCESS;
	}
	
	public String importCorrespondances() {
		String canonicalPath = null;
		try {
			canonicalPath = fichier.getCanonicalPath();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		try {
			List<String> messages = importCorrespondances.lire(canonicalPath);
			if (messages != null)
				if (messages.size() > 0) {
					for (String msg : messages)
						addActionError(msg);
					return INPUT;
				}
		}
		catch (ServiceException e) {
			if (CodeIncident.ERR_CSV_NON_TROUVE.equals(e.getCode())) {
				String message = getText("import.csv.fichier.introuvable");
				message += e.getMessage();
				addActionError(message);
			}
			else {
				String defaut = "defaut";
				List<String> args = new ArrayList<String>();
				args.add("aaa");
				args.add("bbb");
				args.add("ccc");
				String message = getText("import.csv.format.ko", args.toArray(new String[0]));
				message += e.getMessage();
				addActionError(message);
			}
			return INPUT;
		}
		addActionMessage("Correspondances importées.");
		return SUCCESS;
	}
	
	public String importCSVGeneric() {
		String canonicalPath = null;
		try {
			canonicalPath = fichier.getCanonicalPath();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		try {
			lecteurCSVPrincipal.lireCheminFichier(canonicalPath);
		}
		catch (ServiceException e) {
			if (CodeIncident.ERR_CSV_NON_TROUVE.equals(e.getCode())) {
				String message = getText("import.csv.fichier.introuvable");
				message += e.getMessage();
				addActionError(message);
			}
			else {
				String defaut = "defaut";
				List<String> args = new ArrayList<String>();
				String message = e.getMessage();
				addActionError("Format csv multiligne non conforme : "+message);
				e.printStackTrace();
			}
			return INPUT;
		}
		List<ILectureEchange> lecturesEchange = lecteurCSVPrincipal.getLecturesEchange();
		Map<String, String> oldTableauxMarcheObjectIdParRef = new HashMap<String, String>();
		Map<String, String> oldPositionsGeographiquesObjectIdParRef = new HashMap<String, String>();
		Map<String, String> oldObjectIdParOldObjectId = new HashMap<String, String>();
		for (ILectureEchange lectureEchange : lecturesEchange) {
			try {
				List<TableauMarche> tableauxMarche = lectureEchange.getTableauxMarche();
				for (TableauMarche tableauMarche : tableauxMarche)
					if (oldTableauxMarcheObjectIdParRef.get(tableauMarche.getComment()) != null)
						tableauMarche.setObjectId(oldTableauxMarcheObjectIdParRef.get(tableauMarche.getComment()));
				lectureEchange.setTableauxMarche(tableauxMarche);
				
				List<PositionGeographique> positionsGeographiques = lectureEchange.getZonesCommerciales();
				for (PositionGeographique positionGeographique : positionsGeographiques) {
					if (oldPositionsGeographiquesObjectIdParRef.get(positionGeographique.getName()) != null)
						positionGeographique.setObjectId(oldPositionsGeographiquesObjectIdParRef.get(positionGeographique.getName()));
				}
				lectureEchange.setZonesCommerciales(positionsGeographiques);
				
				List<String> objectIdZonesGeneriques = lectureEchange.getObjectIdZonesGeneriques();
				List<String> tmpObjectIdZonesGeneriques = new ArrayList<String>();
				for (String objectId : objectIdZonesGeneriques)
					if (oldObjectIdParOldObjectId.get(objectId) == null)
						tmpObjectIdZonesGeneriques.add(objectId);
					else
						tmpObjectIdZonesGeneriques.add(oldObjectIdParOldObjectId.get(objectId));
				lectureEchange.setObjectIdZonesGeneriques(tmpObjectIdZonesGeneriques);
				
				Map<String, String> zoneParenteParObjectId = lectureEchange.getZoneParenteParObjectId();
				Map<String, String> newZoneParenteParObjectId = new HashMap<String,String>();
				for (String objId : zoneParenteParObjectId.keySet()) {
					String commObjectId = zoneParenteParObjectId.get(objId);
					String newCommObjectId = oldObjectIdParOldObjectId.get(commObjectId);
					if (newCommObjectId == null)
						newZoneParenteParObjectId.put(objId, commObjectId);
					else
						newZoneParenteParObjectId.put(objId, newCommObjectId);
				}
				lectureEchange.setZoneParenteParObjectId(newZoneParenteParObjectId);
				
				importateur.importer(true, lectureEchange);
				
				Map<String, String> _oldTableauxMarcheObjectIdParRef = identificationManager.getDictionaryObjectId().getTableauxMarcheObjectIdParRef();
				for (String key : _oldTableauxMarcheObjectIdParRef.keySet())
					oldTableauxMarcheObjectIdParRef.put(key, _oldTableauxMarcheObjectIdParRef.get(key));
				
				Map<String, String> _oldPositionsGeographiquesObjectIdParRef = identificationManager.getDictionaryObjectId().getPositionsGeographiquesObjectIdParRef();
				for (String key : _oldPositionsGeographiquesObjectIdParRef.keySet())
					oldPositionsGeographiquesObjectIdParRef.put(key, _oldPositionsGeographiquesObjectIdParRef.get(key));
				
				Map<String, String> _oldObjectIdParOldObjectId = identificationManager.getDictionaryObjectId().getObjectIdParOldObjectId();
				for (String key : _oldObjectIdParOldObjectId.keySet())
					oldObjectIdParOldObjectId.put(key, _oldObjectIdParOldObjectId.get(key));
			}
			catch(Exception e) {
				addActionMessage("Impossible de cr&eacute;er la ligne en base");
				log.error("Impossible de cr�er la ligne en base, msg=" + e.getMessage(), e);
				return INPUT;
			}
		}
		identificationManager.getDictionaryObjectId().completion();
		addActionMessage("Cr&eacute;ation des lignes en base effectu&eacute;e");
		return SUCCESS;
	}
	
	public String importCSV() {
		String canonicalPath = null;
		//	Recuperation du chemin du fichier
		try {
			canonicalPath = fichier.getCanonicalPath();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		try {
			lecteurCSV.lireCheminFichier(canonicalPath);
		}
		catch (ServiceException e) {
			if ( CodeIncident.ERR_CSV_NON_TROUVE.equals( e.getCode())) {
				String message = getText( "import.csv.fichier.introuvable");
				message += e.getMessage();
				addActionError( message);
			}
			else {
				String defaut = "defaut";
				List<String> args = new ArrayList<String>();
				args.add( "aaa");
				args.add( "bbb");
				args.add( "ccc");
				String message = getText( "import.csv.format.ko", args.toArray(new String[0]));
				message += e.getMessage();
				addActionError( message);
			}
			return INPUT;
		}
		//	Donnees convertibles en format chouette
		ILectureEchange lectureEchange = lecteurCSV.getLectureEchange();
		//	TODO : Recuperation de l'objet chouettePTNetworkType
		// ChouettePTNetworkType chouettePTNetworkType = exportManager.getExportParRegistration(lectureEchange.getReseau().getRegistrationNumber());
		//	TODO : Validation du fichier CSV
		//	Import des données CSV
		try {
			importateur.importer(true, lectureEchange);
		}
		catch (ServiceException e) {
			addActionMessage("Impossible de cr&eacute;er la ligne en base");
			log.error("Impossible de creer la ligne en base, msg = " + e.getMessage(), e);
			return INPUT;
		}
		addActionMessage("Cr&eacute;ation des lignes en base effectu&eacute;e");
		return SUCCESS;
	}
	
	public String importXMLs() throws Exception {
		String result = SUCCESS;
		ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(fichier));
		ZipEntry zipEntry = zipInputStream.getNextEntry();
		while (zipEntry != null) {
			logger.error("La taille de cette entrée est : "+zipEntry.getSize());
			byte[] bytes = new byte[4096];
			int len = zipInputStream.read(bytes);
			File temp = new File(zipEntry.getName());
			FileOutputStream fos = new FileOutputStream(temp);
			while (len > 0) {
				fos.write(bytes, 0, len);
				len = zipInputStream.read(bytes);
			}
			if (!result.equals(importXML(temp)))
				result = INPUT;
			zipEntry = zipInputStream.getNextEntry();
		}
		return result;
	}
	
	public String importXML() throws Exception {
		return importXML(fichier);
	}
	
	private String importXML(File file) throws Exception {
		String canonicalPath = file.getCanonicalPath();
		ChouettePTNetworkType chouettePTNetworkType = null;
		try {
			logger.debug("IMPORT XML DU FICHIER "+canonicalPath);
			chouettePTNetworkType = lecteurFichierXML.lire(canonicalPath);
			logger.debug("CREATION DU CHOUETTEPTNETWORKTYPE REUSSI");
		}
		catch (Exception exception) {
			gestionException(exception);
			return INPUT;
		}
		ILectureEchange lectureEchange = lecteurEchangeXML.lire(chouettePTNetworkType);
		try {
			importateur.importer(false, lectureEchange);
		}
		catch (ServiceException serviceException) {
			addActionError("Impossible de cr&eacute;er la ligne en base");
			log.error("Impossible de créer la ligne en base, msg=" + serviceException.getMessage(), serviceException);
			return INPUT;
		}
		addActionMessage("Cr&eacute;ation de la ligne \""+lectureEchange.getLigne().getName()+"\" en base effectu&eacute;e");
		return SUCCESS;
	}
	
	public String importAmivifXML() 
	{
		String canonicalPath = copieTemporaire();
		//	Creation de l'objet ChouettePTNetworkType
		RespPTLineStructTimetable amivifLine = null;
		try {
			amivifLine = lecteurAmivifXML.lire(canonicalPath);
		}
		catch (Exception exception) {
			gestionException(exception);
			return INPUT;
		}
		//	Donnees convertibles en format chouette
		ILectureEchange lectureEchange = lecteurEchangeXML.lire( amivifAdapter.getATC(amivifLine));
		//	Import des donnees XML
		try {
			importateur.importer(false, lectureEchange);
		}
		catch (ServiceException serviceException) {
			addActionError("Impossible de cr&eacute;er la ligne en base");
			log.error("Impossible de créer la ligne en base, msg=" + serviceException.getMessage(), serviceException);
			return INPUT;
		}
		addActionMessage("Cr&eacute;ation des lignes en base effectu&eacute;e");
		return SUCCESS;
	}
	
	public String importHorairesItineraire() {
		LecteurCSV lecteurCsvItineraire = new LecteurCSV();
		List<String[]> donneesIn = null;
				
		try {
			donneesIn = lecteurCsvItineraire.lire(fichier);
		}
		catch (IOException e){
			e.printStackTrace();
			String message = getText( "import.csv.fichier.introuvable");
			message += e.getMessage();
			addActionError( message);
			return INPUT_ITINERAIRE;
		}
		catch (ServiceException e) {
			if ( CodeIncident.ERR_CSV_NON_TROUVE.equals( e.getCode())) {
				String message = getText( "import.csv.fichier.introuvable");
				message += e.getMessage();
				addActionError( message);
			}
			else {
				String defaut = "defaut";
				List<String> args = new ArrayList<String>();
				args.add( "aaa");
				args.add( "bbb");
				args.add( "ccc");
				String message = getText( "import.csv.format.ko", args.toArray(new String[0]));
				message += e.getMessage();
				addActionError( message);
			}
			return INPUT_ITINERAIRE;
		}
		
		//	Import des données CSV
		try {
			importHorairesManager.importer(donneesIn);
		}
		catch (ServiceException e) {
			addActionMessage("Impossible d'importer les horaires de l'itineraire");
			log.error("Impossible d'importer les horaires de l'itineraire, msg = " + e.getMessage(), e);
			return INPUT_ITINERAIRE;
		}
		addActionMessage("import des horaires de l'itineraire effectué");
		return SUCCESS_ITINERAIRE;
	}
	
	private void gestionException(Exception exception) {
		if (exception instanceof ServiceException)
		{
			if (exception instanceof fr.certu.chouette.service.validation.commun.ValidationException) {
				fr.certu.chouette.service.validation.commun.ValidationException validationException = (fr.certu.chouette.service.validation.commun.ValidationException) exception;
				//	Liste de codes d'erreur 
				List<TypeInvalidite> codeCategories =  validationException.getCategories();
				for (TypeInvalidite invalidite : codeCategories) {
					//	Liste des messages d'erreur
					Set<String> messages = validationException.getTridentIds(invalidite);
					int count = 0;
					for (String message : messages) {
						if (count > 5) {
							addActionError("etc...");
							break;
						}
						addActionError(message);
						//log.error(message);
						count++;
					}
				}
			}
			else {
				ServiceException serviceException = (ServiceException) exception;
				addActionError("Impossible de r&eacute;cup&eacute;rer le fichier");
				log.error("Impossible de recuperer le fichier, msg = " + serviceException.getMessage(), serviceException);
			}
		}
		//TODO 
	}
	
	private String copieTemporaire() {
		try {
			File temp = File.createTempFile("ligne", ".xml");
			FileCopyUtils.copy(fichier, temp);
		}
		catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		String canonicalPath = null;
		//	Recupération du chemin du fichier
		try {
			canonicalPath = fichier.getCanonicalPath();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return canonicalPath;
	}
	
	@Override
	public String input() throws Exception {
		return INPUT;
	}
	
	public void setImportateur(IImportateur importateur) {
		this.importateur = importateur;
	}
	
	public void setLecteurCSV(ILecteurCSV lecteurCSV) {
		this.lecteurCSV = lecteurCSV;
	}
	
	public void setLecteurCSVPrincipal(ILecteurPrincipal lecteurPrincipal) {
		this.lecteurCSVPrincipal = lecteurPrincipal;
	}
	
	public ILecteurPrincipal getLecteurCSVPrincipal() {
		return lecteurCSVPrincipal;
	}
	
	public void setLecteurCVSHastus(ILecteurPrincipal lecteurPrincipal) {
		this.lecteurCSVHastus = lecteurPrincipal;
	}
	
	public ILecteurPrincipal getLecteurCVSHastus() {
		return lecteurCSVHastus;
	}
	
	public void setLecteurCVSPegase(ILecteurPrincipal lecteurPrincipal) {
		this.lecteurCSVPegase = lecteurPrincipal;
	}
	
	public ILecteurPrincipal getLecteurCVSPegase() {
		return lecteurCSVPegase;
	}
	
	public void setLecteurXMLAltibus(ILecteurPrincipal lecteurXMLAltibus) {
		this.lecteurXMLAltibus = lecteurXMLAltibus;
	}
	
	public String getFichierFileName() {
		return fichierFileName;
	}
	
	public void setFichierFileName(String fichierFileName) {
		this.fichierFileName = fichierFileName;
	}
	
	public File getFichier() {
		return fichier;
	}
	
	public void setFichier(File fichier) {
		this.fichier = fichier;
	}
	
	public boolean isIncremental() {
		return incremental;
	}
	
	public void setIncremental(boolean incremental) {
		this.incremental = incremental;
	}
	
	public String getFichierContentType() {
		return fichierContentType;
	}
	
	public void setFichierContentType(String fichierContentType) {
		log.debug(fichierContentType);
		this.fichierContentType = fichierContentType;
	}
	
	public void setLecteurEchangeXML(ILecteurEchangeXML lecteurEchangeXML) {
		this.lecteurEchangeXML = lecteurEchangeXML;
	}
	
	public void setLecteurFichierXML(ILecteurFichierXML lecteurFichierXML) {
		this.lecteurFichierXML = lecteurFichierXML;
	}
	
	public void setAmivifAdapter(IAmivifAdapter amivifAdapter) {
		this.amivifAdapter = amivifAdapter;
	}
	
	public void setLecteurAmivifXML(ILecteurAmivifXML lecteurAmivifXML) {
		this.lecteurAmivifXML = lecteurAmivifXML;
	}
	
	public String getUseAmivif() {
		return useAmivif;
	}
	
	public void setUseAmivif(String useAmivif) {
		this.useAmivif = useAmivif;
	}
	
	public void setUseCSVGeneric(String useCSVGeneric) {
		this.useCSVGeneric = useCSVGeneric;
	}
	
	public String getUseCSVGeneric() {
		return useCSVGeneric;
	}
	
	public void setUseHastus(String useHastus) {
		this.useHastus = useHastus;
	}
	
	public String getUseHastus() {
		return useHastus;
	}
	
	public void setUseAltibus(String useAltibus) {
		this.useAltibus = useAltibus;
	}
	
	public String getUseAltibus() {
		return useAltibus;
	}
	
	public void setUsePegase(String usePegase) {
		this.usePegase = usePegase;
	}
	
	public String getUsePegase() {
		return usePegase;
	}
	
	public void setIdentificationManager(IIdentificationManager identificationManager) {
		this.identificationManager = identificationManager;
	}
	
	public void setImportCorrespondances(IImportCorrespondances importCorrespondances) {
		this.importCorrespondances = importCorrespondances;
	}
	
	public void setImportHorairesManager(IImportHorairesManager importHorairesManager) {
		this.importHorairesManager = importHorairesManager;
	}
	
	public IIdentificationManager getIdentificationManager() {
		return identificationManager;
	}

	public Long getIdLigne() {
		return idLigne;
	}

	public void setIdLigne(Long idLigne) {
		this.idLigne = idLigne;
	}
	
	public void setLogFileName(String logFileName) {
		this.logFileName = logFileName;
	}
	
	public void setReducteur(IReducteur reducteur) {
		this.reducteur = reducteur;
	}
	
	public void setBaseName(String baseName) {
		this.baseName = baseName;
	}
}
