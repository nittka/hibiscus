/**********************************************************************
 *
 * Copyright (c) 2004 Olaf Willuhn
 * All rights reserved.
 * 
 * This software is copyrighted work licensed under the terms of the
 * Jameica License.  Please consult the file "LICENSE" for details. 
 *
 **********************************************************************/
package de.willuhn.jameica.hbci.gui.controller;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TreeItem;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;

import de.willuhn.datasource.GenericIterator;
import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.gui.AbstractControl;
import de.willuhn.jameica.gui.AbstractView;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.formatter.CurrencyFormatter;
import de.willuhn.jameica.gui.formatter.TreeFormatter;
import de.willuhn.jameica.gui.input.CheckboxInput;
import de.willuhn.jameica.gui.input.DateInput;
import de.willuhn.jameica.gui.input.Input;
import de.willuhn.jameica.gui.input.SelectInput;
import de.willuhn.jameica.gui.parts.Column;
import de.willuhn.jameica.gui.parts.TreePart;
import de.willuhn.jameica.gui.util.Color;
import de.willuhn.jameica.gui.util.Font;
import de.willuhn.jameica.hbci.HBCI;
import de.willuhn.jameica.hbci.HBCIProperties;
import de.willuhn.jameica.hbci.gui.ColorUtil;
import de.willuhn.jameica.hbci.gui.filter.KontoFilter;
import de.willuhn.jameica.hbci.gui.input.DateFromInput;
import de.willuhn.jameica.hbci.gui.input.DateToInput;
import de.willuhn.jameica.hbci.gui.input.KontoInput;
import de.willuhn.jameica.hbci.gui.input.RangeInput;
import de.willuhn.jameica.hbci.gui.parts.EinnahmenAusgabenVerlauf;
import de.willuhn.jameica.hbci.rmi.EinnahmeAusgabeZeitraum;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.server.EinnahmeAusgabe;
import de.willuhn.jameica.hbci.server.EinnahmeAusgabeTreeNode;
import de.willuhn.jameica.hbci.server.KontoUtil;
import de.willuhn.jameica.hbci.server.Range;
import de.willuhn.jameica.hbci.server.UmsatzUtil;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.util.DateUtil;
import de.willuhn.logging.Logger;
import de.willuhn.util.I18N;

/**
 * Controller fuer die Umsatz-Kategorien-Auswertung
 */
public class EinnahmeAusgabeControl extends AbstractControl
{
  private final static I18N i18n = Application.getPluginLoader().getPlugin(HBCI.class).getResources().getI18N();
  private final static de.willuhn.jameica.system.Settings settings = Application.getPluginLoader().getPlugin(HBCI.class).getResources().getSettings();

  private KontoInput kontoAuswahl  = null;
  private CheckboxInput onlyActive = null;
  private DateInput start          = null;
  private DateInput end            = null;
  private RangeInput range         = null;
  private SelectInput interval     = null;

  private List<EinnahmeAusgabeZeitraum> werte = null;
  private TreePart tree            = null;
  private EinnahmenAusgabenVerlauf chart = null;

  /**
   * Gruppierung der Einnahmen/Ausgaben nach Zeitraum.
   */
  private enum Interval
  {
    ALL(-1, -1, i18n.tr("Gesamtzeitraum")), 
    YEAR(Calendar.DAY_OF_YEAR, Calendar.YEAR,i18n.tr("Jahr")),
    MONTH(Calendar.DAY_OF_MONTH, Calendar.MONDAY, i18n.tr("Monat")),
    
    ;

    private String name;
    private int type;
    private int size;

    /**
     * ct.
     * @param type
     * @param size
     * @param name
     */
    private Interval(int type, int size, String name)
    {
      this.name = name;
      this.type = type;
      this.size = size;
    }

    /**
     * @see java.lang.Enum#toString()
     */
    @Override
    public String toString()
    {
      return this.name;
    }
  }

  /**
   * ct.
   * @param view
   */
  public EinnahmeAusgabeControl(AbstractView view)
  {
    super(view);
  }

  /**
   * Liefert eine Auswahlbox fuer das Konto.
   * @return Auswahlbox.
   * @throws RemoteException
   */
  public Input getKontoAuswahl() throws RemoteException
  {
    if (this.kontoAuswahl != null)
      return this.kontoAuswahl;

    this.kontoAuswahl = new KontoInput(null,KontoFilter.ALL);
    this.kontoAuswahl.setPleaseChoose(i18n.tr("<Alle Konten>"));
    this.kontoAuswahl.setSupportGroups(true);
    this.kontoAuswahl.setComment(null);
    this.kontoAuswahl.setRememberSelection("auswertungen.einnahmeausgabe");
    
    return this.kontoAuswahl;
  }

  /**
   * Liefert ein Auswahl-Feld fuer das Start-Datum.
   * @return Auswahl-Feld.
   */
  public Input getStart()
  {
    if (this.start != null)
      return this.start;

    this.start = new DateFromInput(null, "auswertungen.einnahmeausgabe.filter.from");
    this.start.setName(i18n.tr("Von"));
    this.start.setComment(null);
    return this.start;
  }
  
  /**
   * Liefert die Checkbox, mit der eingestellt werden kann, ob nur aktive Konten angezeigt werden sollen.
   * @return Checkbox.
   */
  public CheckboxInput getActiveOnly()
  {
    if (this.onlyActive != null)
      return this.onlyActive;
    
    this.onlyActive = new CheckboxInput(settings.getBoolean("auswertungen.einnahmeausgabe.filter.active",false));
    this.onlyActive.setName(i18n.tr("Nur aktive Konten"));
    this.onlyActive.addListener(new org.eclipse.swt.widgets.Listener() {

      @Override
      public void handleEvent(Event event)
      {
        settings.setAttribute("auswertungen.einnahmeausgabe.filter.active", (Boolean) onlyActive.getValue());
      }
    });
    return this.onlyActive;
  }
  

  /**
   * Liefert eine Auswahl mit Zeit-Presets.
   * @return eine Auswahl mit Zeit-Presets.
   */
  public RangeInput getRange()
  {
    if (this.range != null)
      return this.range;
    
    this.range = new RangeInput(this.getStart(), this.getEnd(), Range.CATEGORY_AUSWERTUNG, "auswertungen.einnahmeausgabe.filter.range");
    return this.range;
  }

  /**
   * Liefert ein Auswahl-Feld fuer das End-Datum.
   * @return Auswahl-Feld.
   */
  public Input getEnd()
  {
    if (this.end != null)
      return this.end;

    this.end = new DateToInput(null, "auswertungen.einnahmeausgabe.filter.to");
    this.end.setName(i18n.tr("bis"));
    this.end.setComment(null);
    return this.end;
  }

  /**
   * Liefert ein Auswahl-Feld f�r die zeitliche Gruppierung.
   * @return Auswahl-Feld
   * */
  public SelectInput getInterval()
  {
    if (this.interval != null)
      return this.interval;

    this.interval = new SelectInput(Interval.values(), Interval.valueOf(settings.getString("auswertungen.einnahmeausgabe.filter.interval", "MONTH")));
    this.interval.setName(i18n.tr("Gruppierung nach"));
    this.interval.addListener(new Listener() {

      @Override
      public void handleEvent(Event event)
      {
        Interval value = (Interval) interval.getValue();
        settings.setAttribute("auswertungen.einnahmeausgabe.filter.interval", value.name());
      }

    });
    return this.interval;
  }
  
  /**
   * Liefert ein Balkendiagramm bei dem Ausgaben und Einnahmen gegen�bergestellt werden 
   * @return Balkendiagramm
   * @throws RemoteException 
   */
  public EinnahmenAusgabenVerlauf getChart() throws RemoteException
  {
    if(this.chart != null)
      return this.chart;
    
    this.chart = new EinnahmenAusgabenVerlauf(getWerte());
    return chart;
  }

  /**
   * Liefert eine Tabelle mit den Einnahmen/Ausgaben und Salden
   * @return Tabelle mit den Einnahmen/Ausgaben und Salden
   * @throws RemoteException
   */
  public TreePart getTree() throws RemoteException
  {
    if (this.tree != null)
      return this.tree;

    tree = new TreePart(getWerte(), null);
    tree.addColumn(i18n.tr("Konto"),        "text");
    tree.addColumn(i18n.tr("Anfangssaldo"), "anfangssaldo",new CurrencyFormatter(HBCIProperties.CURRENCY_DEFAULT_DE, HBCI.DECIMALFORMAT), false, Column.ALIGN_RIGHT);
    tree.addColumn(i18n.tr("Einnahmen"),    "einnahmen",   new CurrencyFormatter(HBCIProperties.CURRENCY_DEFAULT_DE, HBCI.DECIMALFORMAT), false, Column.ALIGN_RIGHT);
    tree.addColumn(i18n.tr("Ausgaben"),     "ausgaben",    new CurrencyFormatter(HBCIProperties.CURRENCY_DEFAULT_DE, HBCI.DECIMALFORMAT), false, Column.ALIGN_RIGHT);
    tree.addColumn(i18n.tr("Endsaldo"),     "endsaldo",    new CurrencyFormatter(HBCIProperties.CURRENCY_DEFAULT_DE, HBCI.DECIMALFORMAT), false, Column.ALIGN_RIGHT);
    tree.addColumn(i18n.tr("Plus/Minus"),   "plusminus",   new CurrencyFormatter(HBCIProperties.CURRENCY_DEFAULT_DE, HBCI.DECIMALFORMAT), false, Column.ALIGN_RIGHT);
    tree.addColumn(i18n.tr("Differenz"),    "differenz",   new CurrencyFormatter(HBCIProperties.CURRENCY_DEFAULT_DE, HBCI.DECIMALFORMAT), false, Column.ALIGN_RIGHT);

    tree.setFormatter(new TreeFormatter()
    {
      /**
       * @see de.willuhn.jameica.gui.formatter.TableFormatter#format(org.eclipse.swt.widgets.TableItem)
       */
      public void format(TreeItem item)
      {
        if (item == null || item.getData() instanceof EinnahmeAusgabeTreeNode)
          return;
        
        EinnahmeAusgabe ea = (EinnahmeAusgabe) item.getData();
        boolean summe = ea.isSumme();
        try
        {
          double plusminus = ea.getPlusminus();
          if (summe)
          {
            item.setForeground(Color.FOREGROUND.getSWTColor());
          }
          else
          {
            Konto k = ea.getKonto();
            if (k != null && k.hasFlag(Konto.FLAG_DISABLED))
              item.setForeground(Color.COMMENT.getSWTColor());
            else
              item.setForeground(ColorUtil.getForeground(plusminus));
            item.setFont(ea.hasDiff() && !summe ? Font.BOLD.getSWTFont() : Font.DEFAULT.getSWTFont());
          }
          
        }
        catch (Exception e)
        {
          Logger.error("unable to format line", e);
        }
      }
    });

    tree.setRememberColWidths(true);
    return tree;
  }

  /**
   * Ermittelt die Liste der Knoten f�r den Baum. Wenn keine Aufschl�sselung gew�nscht ist,
   * werden die Zeilen ohne Elternknoten angezeigt.
   * @return Liste mit den Werten.
   * @throws RemoteException
   */
  private List<EinnahmeAusgabeZeitraum> getWerte() throws RemoteException
  {
    if (this.werte != null)
    {
      return this.werte;
    }

    Date start = (Date) this.getStart().getValue();
    Date end = (Date) this.getEnd().getValue();

    List<Konto> konten = getSelectedAccounts();
    List<Umsatz> umsatzList = getUmsaetze(konten, start, end);
    if (!umsatzList.isEmpty())
    // bei offenen Zeitr�umen k�nnen wir den ersten und letzten Umsatztag ermitteln
    {
      if (start == null)
      {
        start = umsatzList.get(0).getDatum();
      }
      if (end == null)
      {
        end = umsatzList.get(umsatzList.size() - 1).getDatum();
      }
    }

    // wenn die Umsatzliste leer ist, erfolgt keine Gruppierung, es wird nur der Gesamtzeitraum
    // ausgewertet und da keine Ums�tze zugeordnet werden m�ssen, spielen fehlende Datumsangaben keine Rolle
    Interval interval = umsatzList.isEmpty() ? Interval.ALL : (Interval) getInterval().getValue();
    List<EinnahmeAusgabeTreeNode> result = createEmptyNodes(start, end, konten, interval);
    addData(result, umsatzList);

    this.werte = new ArrayList<EinnahmeAusgabeZeitraum>();
    if (interval == Interval.ALL)
    {
      // Es gibt nur einen Zweig - da reichen uns die darunterliegenden Elemente
      this.werte.addAll(getChildren(result.get(0)));
    } else
    {
      this.werte.addAll(result);
    }
    return this.werte;
  }

  private List<Umsatz> getUmsaetze(List<Konto> konten, Date start, Date end) throws RemoteException
  {
    List<String> kontoIds = new ArrayList<String>();
    for (Konto konto : konten)
    {
      kontoIds.add(konto.getID());
    }
    DBIterator umsaetze = UmsatzUtil.getUmsaetze();
    if (start != null)
    {
      umsaetze.addFilter("datum >= ?", new java.sql.Date(DateUtil.startOfDay(start).getTime()));
    }
    if (end != null)
    {
      umsaetze.addFilter("datum <= ?", new java.sql.Date(DateUtil.endOfDay(end).getTime()));
    }
    // TODO funktioniert das mit allen unterst�tzten Datenbankversionen?
    umsaetze.addFilter("konto_id in (" + Joiner.on(",").join(kontoIds) + ")");
    List<Umsatz> umsatzList = new ArrayList<Umsatz>();
    while (umsaetze.hasNext())
    {
      Umsatz u = (Umsatz) umsaetze.next();
      if (!u.hasFlag(Umsatz.FLAG_NOTBOOKED))
      {
        umsatzList.add(u);
      }
    }
    return umsatzList;
  }

  private void addData(List<EinnahmeAusgabeTreeNode> nodes, List<Umsatz> umsatzList) throws RemoteException
  {
    setInitialSalden(nodes.get(0), umsatzList);
    int index = 0;
    EinnahmeAusgabeTreeNode currentNode = null;
    // Map der Daten f�r eine Konto-ID f�r schnelles Zuweisen der Ums�tze
    Map<String, EinnahmeAusgabe> kontoData = null;
    for (Umsatz umsatz : umsatzList)
    {
      // Daten f�r das n�chste relevante Intervall vorbereiten; 'while' da es m�glich w�re, dass es f�r einen Zeitraum in der Mitte gar keine Ums�tze gab
      while (currentNode == null || umsatz.getDatum().after(currentNode.getEnddatum()))
      {
        EinnahmeAusgabeTreeNode oldNode = currentNode;
        currentNode = nodes.get(index++);
        saldenUebertrag(oldNode, currentNode);
        kontoData = getKontoDataMap(currentNode);
      }

      EinnahmeAusgabe ea = kontoData.get(umsatz.getKonto().getID());
      ea.addUmsatz(umsatz);
    }
    // Salden�bertrag f�r die verbliebenen Zeitr�ume (z.B. dieses Jahr monatlich gruppiert die kommenden Monate)
    // alternativ k�nnte man Zeitr�ume ohne Daten hinten auch entfernen
    for (int i = index - 1; i >= 0 && i < nodes.size() - 1; i++)
    {
      saldenUebertrag(nodes.get(i), nodes.get(i + 1));
    }
    calculateSums(nodes);
  }

  private Map<String, EinnahmeAusgabe> getKontoDataMap(EinnahmeAusgabeTreeNode node) throws RemoteException
  {
    Map<String, EinnahmeAusgabe> kontoData = new HashMap<>();
    List<EinnahmeAusgabe> eaList = getChildren(node);
    for (EinnahmeAusgabe ea : eaList)
    {
      if (ea.getKonto() != null)
      {
        kontoData.put(ea.getKonto().getID(), ea);
      }
    }
    return kontoData;
  }

  // �bernehme Salden in den Nachfolgerzeitraum, falls es dort keine Ums�tze gibt, aus denen die Salden verwendet werden
  private void saldenUebertrag(EinnahmeAusgabeTreeNode von, EinnahmeAusgabeTreeNode nach) throws RemoteException
  {
    if (von != null && nach != null)
    {
      Map<String, EinnahmeAusgabe> vonMap = getKontoDataMap(von);
      Map<String, EinnahmeAusgabe> nachMap = getKontoDataMap(nach);
      for (Entry<String, EinnahmeAusgabe> vonEntry : vonMap.entrySet())
      {
        EinnahmeAusgabe vonData = vonEntry.getValue();
        EinnahmeAusgabe nachData = nachMap.get(vonEntry.getKey());
        nachData.setAnfangssaldo(vonData.getEndsaldo());
        nachData.setEndsaldo(vonData.getEndsaldo());
      }
    }
  }

  private void setInitialSalden(EinnahmeAusgabeTreeNode node, List<Umsatz> umsatzList) throws RemoteException
  {
    Date startDate = umsatzList.isEmpty() ? null : umsatzList.get(0).getDatum();
    for (EinnahmeAusgabe ea : getChildren(node))
    {
      if (ea.getKonto() != null)
      {
        boolean umsatzGefunden = false;
        String id = ea.getKonto().getID();
        for (Umsatz u : umsatzList)
        {
          // suche ersten Umsatz des Kontos in der Umsatzliste
          if (u.getKonto().getID().equals(id))
          {
            umsatzGefunden = true;
            ea.setAnfangssaldo(u.getSaldo() - u.getBetrag());
            ea.setEndsaldo(ea.getAnfangssaldo());
            break;
          }
        }
        // falls es keinen gibt, Fallback Bestandslogik - nicht schnell, aber nur einmal pro Konto
        if (!umsatzGefunden)
        {
          double saldo = KontoUtil.getAnfangsSaldo(ea.getKonto(), startDate);
          ea.setAnfangssaldo(saldo);
          ea.setEndsaldo(saldo);
        }
      }
    }
  }

  private void calculateSums(List<EinnahmeAusgabeTreeNode> nodes) throws RemoteException
  {
    for (EinnahmeAusgabeTreeNode node : nodes)
    {
      List<EinnahmeAusgabe> list = getChildren(node);
      // Alle Konten
      double summeAnfangssaldo = 0.0d;
      double summeEinnahmen = 0.0d;
      double summeAusgaben = 0.0d;
      double summeEndsaldo = 0.0d;
      EinnahmeAusgabe sumElement = null;
      for (EinnahmeAusgabe ea : list)
      {
        if (!ea.isSumme())
        {
          summeAnfangssaldo += ea.getAnfangssaldo();
          summeEinnahmen += ea.getEinnahmen();
          summeAusgaben += ea.getAusgaben();
          summeEndsaldo += ea.getEndsaldo();
        } else if (sumElement != null)
        {
          throw new IllegalStateException("implementation error - there must be only one sum element");
        } else
        {
          sumElement = ea;
        }
      }
      if (sumElement != null)
      {
        sumElement.setAnfangssaldo(summeAnfangssaldo);
        sumElement.setEndsaldo(summeEndsaldo);
        sumElement.setEinnahmen(summeEinnahmen);
        sumElement.setAusgaben(summeAusgaben);
      }
    }
  }

  private List<EinnahmeAusgabe> getChildren(EinnahmeAusgabeTreeNode treeNode) throws RemoteException
  {
    List<EinnahmeAusgabe> result = new ArrayList<>();
    GenericIterator iterator = treeNode.getChildren();
    while (iterator.hasNext())
    {
      result.add((EinnahmeAusgabe) iterator.next());
    }
    return result;
  }

  private List<Konto> getSelectedAccounts() throws RemoteException
  {
    List<Konto> result = new ArrayList<>();
    Object o = getKontoAuswahl().getValue();
    if (o instanceof Konto)
    {
      result.add((Konto) o);
    } else if (o == null || (o instanceof String))
    {
      boolean onlyActive = ((Boolean) this.getActiveOnly().getValue()).booleanValue();
      settings.setAttribute("umsatzlist.filter.active", onlyActive);
      String group = o != null && (o instanceof String) ? (String) o : null;

      List<Konto> konten = KontoUtil.getKonten(onlyActive ? KontoFilter.ACTIVE : KontoFilter.ALL);
      for (Konto k : konten)
      {
        if (group == null || Objects.equal(group, k.getKategorie()))
        {
          result.add(k);
        }
      }
    }
    return result;
  }

  /**
   * Erstelle die finale Struktur - nur ohne Betr�ge und Salden
   */
  private List<EinnahmeAusgabeTreeNode> createEmptyNodes(Date start, Date end, List<Konto> konten, Interval interval) throws RemoteException
  {
    List<EinnahmeAusgabeTreeNode> result = new ArrayList<>();
    if (interval == Interval.ALL)
    {
      List<EinnahmeAusgabe> kontoNodes = getEmptyNodes(start, end, konten);
      EinnahmeAusgabeTreeNode node = new EinnahmeAusgabeTreeNode(start, end, kontoNodes);
      result.add(node);
    } else if (start == null || end == null)
    {
      throw new IllegalStateException("programming error - if there is grouping, there must be transactions and hence both dates are set");
    } else
    {
      Calendar calendar = Calendar.getInstance();
      calendar.setTime(DateUtil.startOfDay(start));
      while (calendar.getTime().before(end))
      {
        calendar.set(interval.type, 1);
        Date nodeFrom = calendar.getTime();

        // ermittle den Zeipunkt unmittelbar vor dem n�chsten Zeitraumstart
        calendar.add(interval.size, 1);
        calendar.setTimeInMillis(calendar.getTime().getTime() - 1);
        Date nodeTo = DateUtil.startOfDay(calendar.getTime());

        List<EinnahmeAusgabe> werte = getEmptyNodes(nodeFrom, nodeTo, konten);
        result.add(new EinnahmeAusgabeTreeNode(nodeFrom, nodeTo, werte));
        // ermittle den Start des n�chsten Zeitraums
        calendar.setTime(nodeFrom);
        calendar.add(interval.size, 1);
      }
    }
    return result;
  }

  private List<EinnahmeAusgabe> getEmptyNodes(Date start, Date end, List<Konto> konten) throws RemoteException
  {
    List<EinnahmeAusgabe> result = new ArrayList<>();
    for (Konto konto : konten)
    {
      EinnahmeAusgabe ea = new EinnahmeAusgabe(konto);
      ea.setStartdatum(start);
      ea.setEnddatum(end);
      result.add(ea);
    }
    if (konten.size() > 1)
    {
      EinnahmeAusgabe summe = new EinnahmeAusgabe();
      summe.setStartdatum(start);
      summe.setEnddatum(end);
      summe.setIsSumme(true);
      result.add(summe);
    }
    return result;
  }

  /**
   * Aktualisiert die Tabelle.
   */
  public void handleReload()
  {
    try
    {
      TreePart tree = this.getTree();
      tree.removeAll();
      this.werte = null;
      
      Date tStart = (Date) getStart().getValue();
      Date tEnd = (Date) getEnd().getValue();
      if (tStart != null && tEnd != null && tStart.after(tEnd))
      {
        GUI.getView().setErrorText(i18n.tr("Das Anfangsdatum muss vor dem Enddatum liegen"));
        return;
      }
      
      tree.setList(this.getWerte());
      
      EinnahmenAusgabenVerlauf chart = getChart();
      chart.setList(this.getWerte());
    }
    catch (RemoteException re)
    {
      Logger.error("unable to redraw table",re);
      Application.getMessagingFactory().sendMessage(new StatusBarMessage(i18n.tr("Fehler beim Aktualisieren"), StatusBarMessage.TYPE_ERROR));
    }
  }
}
