/**********************************************************************
 *
 * Copyright (c) 2004 Olaf Willuhn
 * All rights reserved.
 * 
 * This software is copyrighted work licensed under the terms of the
 * Jameica License.  Please consult the file "LICENSE" for details. 
 *
 **********************************************************************/

package de.willuhn.jameica.hbci.gui.input;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;

import de.willuhn.jameica.gui.input.AbstractInput;

public class KontoInput2 extends AbstractInput
{

  private Combo control;

  @Override
  public Control getControl()
  {
    if(control == null)
    {
      initControl();
    }
    return control;
  }

  private void initControl()
  {
    control=new Combo(getParent(), SWT.MULTI);
    control.add("tada");
    control.add("tidum");
  }

  @Override
  public Object getValue()
  {
    // TODO Auto-generated method stub
    return null;
  }
  @Override
  public void setValue(Object value)
  {
    // TODO Auto-generated method stub
    
  }
  @Override
  public void focus()
  {
    // TODO Auto-generated method stub
    
  }
  @Override
  public void disable()
  {
    // TODO Auto-generated method stub
    
  }
  @Override
  public void enable()
  {
    // TODO Auto-generated method stub
    
  }
  @Override
  public void setEnabled(boolean enabled)
  {
    // TODO Auto-generated method stub
    
  }
  @Override
  public boolean isEnabled()
  {
    // TODO Auto-generated method stub
    return false;
  }
}
