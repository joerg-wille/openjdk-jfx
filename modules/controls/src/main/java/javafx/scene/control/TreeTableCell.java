/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package javafx.scene.control;

import javafx.css.PseudoClass;
import com.sun.javafx.scene.control.skin.TreeTableCellSkin;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.event.Event;

import javafx.collections.WeakListChangeListener;
import java.lang.ref.WeakReference;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.scene.control.TreeTableColumn.CellEditEvent;


/**
 * Represents a single row/column intersection in a {@link TreeTableView}. To 
 * represent this intersection, a TreeTableCell contains an 
 * {@link #indexProperty() index} property, as well as a 
 * {@link #tableColumnProperty() tableColumn} property. In addition, a TreeTableCell
 * instance knows what {@link TreeTableRow} it exists in.
 * 
 * @see TreeTableView
 * @see TreeTableColumn
 * @see Cell
 * @see IndexedCell
 * @see TreeTableRow
 * @param <T> The type of the item contained within the Cell.
 * @since JavaFX 8.0
 */
public class TreeTableCell<S,T> extends IndexedCell<T> {
    
    /***************************************************************************
     *                                                                         *
     * Constructors                                                            *
     *                                                                         *
     **************************************************************************/

    /**
     * Constructs a default TreeTableCell instance with a style class of
     * 'tree-table-cell'.
     */
    public TreeTableCell() {
        getStyleClass().addAll(DEFAULT_STYLE_CLASS);
        
        updateColumnIndex();
    }



    /***************************************************************************
     *                                                                         *
     * Callbacks and Events                                                    *
     *                                                                         *
     **************************************************************************/
    
    private boolean itemDirty = false;
    
    /*
     * This is the list observer we use to keep an eye on the SelectedCells
     * ObservableList in the tree table view. Because it is possible that the table can
     * be mutated, we create this observer here, and add/remove it from the
     * storeTableView method.
     */
    private ListChangeListener<TreeTablePosition<S,?>> selectedListener = new ListChangeListener<TreeTablePosition<S,?>>() {
        @Override public void onChanged(Change<? extends TreeTablePosition<S,?>> c) {
            updateSelection();
        }
    };

    // same as above, but for focus
    private final InvalidationListener focusedListener = new InvalidationListener() {
        @Override public void invalidated(@SuppressWarnings("unused") Observable value) {
            updateFocus();
        }
    };

    // same as above, but for for changes to the properties on TableRow
    private final InvalidationListener tableRowUpdateObserver = new InvalidationListener() {
        @Override public void invalidated(@SuppressWarnings("unused") Observable value) {
            itemDirty = true;
            requestLayout();
        }
    };
    
    private final InvalidationListener editingListener = new InvalidationListener() {
        @Override public void invalidated(@SuppressWarnings("unused") Observable value) {
            updateEditing();
        }
    };
    
    private ListChangeListener<TreeTableColumn<S,?>> visibleLeafColumnsListener = new ListChangeListener<TreeTableColumn<S,?>>() {
        @Override public void onChanged(Change<? extends TreeTableColumn<S,?>> c) {
            updateColumnIndex();
        }
    };
    
    private ListChangeListener<String> columnStyleClassListener = new ListChangeListener<String>() {
        @Override public void onChanged(Change<? extends String> c) {
            while (c.next()) {
                if (c.wasRemoved()) {
                    getStyleClass().removeAll(c.getRemoved());
                }
                
                if (c.wasAdded()) {
                    getStyleClass().addAll(c.getAddedSubList());
                }
            }
        }
    };

    private final InvalidationListener rootPropertyListener = new InvalidationListener() {
        @Override public void invalidated(Observable observable) {
            updateItem();
        }
    };
    
    private final WeakListChangeListener<TreeTablePosition<S,?>> weakSelectedListener = 
            new WeakListChangeListener<TreeTablePosition<S,?>>(selectedListener);
    private final WeakInvalidationListener weakFocusedListener = 
            new WeakInvalidationListener(focusedListener);
    private final WeakInvalidationListener weaktableRowUpdateObserver = 
            new WeakInvalidationListener(tableRowUpdateObserver);
    private final WeakInvalidationListener weakEditingListener = 
            new WeakInvalidationListener(editingListener);
    private final WeakListChangeListener<TreeTableColumn<S,?>> weakVisibleLeafColumnsListener =
            new WeakListChangeListener<TreeTableColumn<S,?>>(visibleLeafColumnsListener);
    private final WeakListChangeListener<String> weakColumnStyleClassListener =
            new WeakListChangeListener<String>(columnStyleClassListener);
    private final WeakInvalidationListener weakRootPropertyListener =
            new WeakInvalidationListener(rootPropertyListener);

    
    /***************************************************************************
     *                                                                         *
     * Properties                                                              *
     *                                                                         *
     **************************************************************************/
    
    // --- TableColumn
    /**
     * The TreeTableColumn instance that backs this TreeTableCell.
     */
    private ReadOnlyObjectWrapper<TreeTableColumn<S,T>> treeTableColumn = 
            new ReadOnlyObjectWrapper<TreeTableColumn<S,T>>(this, "treeTableColumn") {
        @Override protected void invalidated() {
            updateColumnIndex();
        }
    };
    public final ReadOnlyObjectProperty<TreeTableColumn<S,T>> tableColumnProperty() { return treeTableColumn.getReadOnlyProperty(); }
    private void setTableColumn(TreeTableColumn<S,T> value) { treeTableColumn.set(value); }
    public final TreeTableColumn<S,T> getTableColumn() { return treeTableColumn.get(); }
    
    
    // --- TableView
    /**
     * The TreeTableView associated with this TreeTableCell.
     */
    private ReadOnlyObjectWrapper<TreeTableView<S>> treeTableView;
    private void setTreeTableView(TreeTableView<S> value) {
        treeTableViewPropertyImpl().set(value);
    }
    public final TreeTableView<S> getTreeTableView() {
        return treeTableView == null ? null : treeTableView.get();
    }
    public final ReadOnlyObjectProperty<TreeTableView<S>> treeTableViewProperty() {
        return treeTableViewPropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyObjectWrapper<TreeTableView<S>> treeTableViewPropertyImpl() {
        if (treeTableView == null) {
            treeTableView = new ReadOnlyObjectWrapper<TreeTableView<S>>(this, "treeTableView") {
                private WeakReference<TreeTableView<S>> weakTableViewRef;
                @Override protected void invalidated() {
                    TreeTableView.TreeTableViewSelectionModel<S> sm;
                    TreeTableView.TreeTableViewFocusModel<S> fm;
                    
                    if (weakTableViewRef != null) {
                        TreeTableView<S> oldTableView = weakTableViewRef.get();
                        if (oldTableView != null) {
                            sm = oldTableView.getSelectionModel();
                            if (sm != null) {
                                sm.getSelectedCells().removeListener(weakSelectedListener);
                            }

                            fm = oldTableView.getFocusModel();
                            if (fm != null) {
                                fm.focusedCellProperty().removeListener(weakFocusedListener);
                            }

                            oldTableView.editingCellProperty().removeListener(weakEditingListener);
                            oldTableView.getVisibleLeafColumns().removeListener(weakVisibleLeafColumnsListener);
                            oldTableView.rootProperty().removeListener(weakRootPropertyListener);
                        }
                    }

                    TreeTableView newTreeTableView = get();
                    if (newTreeTableView != null) {
                        sm = newTreeTableView.getSelectionModel();
                        if (sm != null) {
                            sm.getSelectedCells().addListener(weakSelectedListener);
                        }

                        fm = newTreeTableView.getFocusModel();
                        if (fm != null) {
                            fm.focusedCellProperty().addListener(weakFocusedListener);
                        }

                        newTreeTableView.editingCellProperty().addListener(weakEditingListener);
                        newTreeTableView.getVisibleLeafColumns().addListener(weakVisibleLeafColumnsListener);
                        newTreeTableView.rootProperty().addListener(weakRootPropertyListener);
                        
                        weakTableViewRef = new WeakReference<TreeTableView<S>>(newTreeTableView);
                    }
                    
                    updateColumnIndex();
                }
            };
        }
        return treeTableView;
    }
    
    
    // --- TableRow
    /**
     * The TreeTableRow that this TreeTableCell currently finds itself placed within.
     */
    private ReadOnlyObjectWrapper<TreeTableRow<S>> treeTableRow = 
            new ReadOnlyObjectWrapper<TreeTableRow<S>>(this, "treeTableRow");
    private void setTreeTableRow(TreeTableRow<S> value) { treeTableRow.set(value); }
    public final TreeTableRow<S> getTreeTableRow() { return treeTableRow.get(); }
    public final ReadOnlyObjectProperty<TreeTableRow<S>> tableRowProperty() { return treeTableRow;  }



    /***************************************************************************
     *                                                                         *
     * Editing API                                                             *
     *                                                                         *
     **************************************************************************/

    /** {@inheritDoc} */
    @Override public void startEdit() {
        if (isEditing()) return;

        final TreeTableView<S> table = getTreeTableView();
        final TreeTableColumn<S,T> column = getTableColumn();
        if (! isEditable() ||
                (table != null && ! table.isEditable()) ||
                (column != null && ! getTableColumn().isEditable())) {
            return;
        }

        updateItem();

        // it makes sense to get the cell into its editing state before firing
        // the event to listeners below, so that's what we're doing here
        // by calling super.startEdit().
        super.startEdit();
        
        @SuppressWarnings("unchecked")
        TreeTablePosition<S,T> editingCell = (TreeTablePosition<S,T>) table.getEditingCell();
        
        if (column != null) {
            CellEditEvent<S,T> editEvent = new CellEditEvent<S,T>(
                table,
                editingCell,
                TreeTableColumn.<S,T>editStartEvent(),
                null
            );

            Event.fireEvent(column, editEvent);
        }
    }

    /** {@inheritDoc} */
    @Override public void commitEdit(T newValue) {
        if (! isEditing()) return;
        
        final TreeTableView<S> table = getTreeTableView();
        if (table != null) {
            @SuppressWarnings("unchecked")
            TreeTablePosition<S,T> editingCell = (TreeTablePosition<S,T>) table.getEditingCell();
            
            // Inform the TableView of the edit being ready to be committed.
            CellEditEvent<S,T> editEvent = new CellEditEvent<S,T>(
                table,
                editingCell,
                TreeTableColumn.<S,T>editCommitEvent(),
                newValue
            );

            Event.fireEvent(getTableColumn(), editEvent);
        }

        // inform parent classes of the commit, so that they can switch us
        // out of the editing state.
        // This MUST come before the updateItem call below, otherwise it will
        // call cancelEdit(), resulting in both commit and cancel events being
        // fired (as identified in RT-29650)
        super.commitEdit(newValue);

        // update the item within this cell, so that it represents the new value
        updateItem(newValue, false);

        if (table != null) {
            // reset the editing cell on the TableView
            table.edit(-1, null);
            table.requestFocus();
        }
    }

    /** {@inheritDoc} */
    @Override public void cancelEdit() {
        if (! isEditing()) return;

        final TreeTableView<S> table = getTreeTableView();

        super.cancelEdit();

        // reset the editing index on the TableView
        if (table != null) {
            @SuppressWarnings("unchecked")
            TreeTablePosition<S,T> editingCell = (TreeTablePosition<S,T>) table.getEditingCell();
            
            if (updateEditingIndex) table.edit(-1, null);

            table.requestFocus();

            CellEditEvent<S,T> editEvent = new CellEditEvent<S,T>(
                table,
                editingCell,
                TreeTableColumn.<S,T>editCancelEvent(),
                null
            );

            Event.fireEvent(getTableColumn(), editEvent);
        }
    }
    
    
    
    /* *************************************************************************
     *                                                                         *
     * Overriding methods                                                      *
     *                                                                         *
     **************************************************************************/

    /** {@inheritDoc} */
    @Override public void updateSelected(boolean selected) {
        // copied from Cell, with the first conditional clause below commented 
        // out, as it is valid for an empty TableCell to be selected, as long 
        // as the parent TableRow is not empty (see RT-15529).
        /*if (selected && isEmpty()) return;*/
        if (getTreeTableRow() == null || getTreeTableRow().isEmpty()) return;
        setSelected(selected);
    }

    

    /* *************************************************************************
     *                                                                         *
     * Private Implementation                                                  *
     *                                                                         *
     **************************************************************************/

    private int index = -1;

    @Override void indexChanged() {
        super.indexChanged();
        // Ideally we would just use the following two lines of code, rather
        // than the updateItem() call beneath, but if we do this we end up with
        // RT-22428 where all the columns are collapsed.
        // itemDirty = true;
        // requestLayout();

        final int oldIndex = index;
        super.indexChanged();
        index = getIndex();

        if (isEditing() && index == oldIndex) {
            // no-op
            // Fix for RT-31165 - if we (needlessly) update the index whilst the
            // cell is being edited it will no longer be in an editing state.
            // This means that in certain (common) circumstances that it will
            // appear that a cell is uneditable as, despite being clicked, it
            // will not change to the editing state as a layout of VirtualFlow
            // is immediately invoked, which forces all cells to be updated.
        } else {
            updateItem();
            updateSelection();
            updateFocus();
        }
    }
    
    private boolean isLastVisibleColumn = false;
    private int columnIndex = -1;
    
    private void updateColumnIndex() {
        final TreeTableView<S> tv = getTreeTableView();
        TreeTableColumn<S,T> tc = getTableColumn();
        columnIndex = tv == null || tc == null ? -1 : tv.getVisibleLeafIndex(tc);
        
        // update the pseudo class state regarding whether this is the last
        // visible cell (i.e. the right-most). 
        isLastVisibleColumn = getTableColumn() != null &&
                columnIndex != -1 && 
                columnIndex == tv.getVisibleLeafColumns().size() - 1;
        pseudoClassStateChanged(PSEUDO_CLASS_LAST_VISIBLE, isLastVisibleColumn);
    }

    private void updateSelection() {
        /*
         * This cell should be selected if the selection mode of the table
         * is cell-based, and if the row and column that this cell represents
         * is selected.
         *
         * If the selection mode is not cell-based, then the listener in the
         * TableRow class might pick up the need to set an entire row to be
         * selected.
         */
        if (isEmpty()) return;
        
        final TreeTableView<S> tv = getTreeTableView();
        if (getIndex() == -1 || getTreeTableView() == null) return;
        if (tv.getSelectionModel() == null) return;
        
        boolean isSelected = isInCellSelectionMode() &&
                tv.getSelectionModel().isSelected(getIndex(), getTableColumn());
        if (isSelected() == isSelected) return;

        updateSelected(isSelected);
    }

    private void updateFocus() {
        final TreeTableView<S> tv = getTreeTableView();
        if (getIndex() == -1 || tv == null) return;
        if (tv.getFocusModel() == null) return;
        
        boolean isFocused = isInCellSelectionMode() &&
                tv.getFocusModel() != null &&
                tv.getFocusModel().isFocused(getIndex(), getTableColumn());

        setFocused(isFocused);
    }

    private void updateEditing() {
        final TreeTableView<S> tv = getTreeTableView();
        if (getIndex() == -1 || tv == null) return;

        TreeTablePosition<S,?> editCell = tv.getEditingCell();
        boolean match = match(editCell);
        
        if (match && ! isEditing()) {
            startEdit();
        } else if (! match && isEditing()) {
            // If my index is not the one being edited then I need to cancel
            // the edit. The tricky thing here is that as part of this call
            // I cannot end up calling list.edit(-1) the way that the standard
            // cancelEdit method would do. Yet, I need to call cancelEdit
            // so that subclasses which override cancelEdit can execute. So,
            // I have to use a kind of hacky flag workaround.
            updateEditingIndex = false;
            cancelEdit();
            updateEditingIndex = true;
        }
    }
    private boolean updateEditingIndex = true;

    private boolean match(TreeTablePosition pos) {
        return pos != null && pos.getRow() == getIndex() && pos.getTableColumn() == getTableColumn();
    }

    private boolean isInCellSelectionMode() {
        TreeTableView<S> treeTable = getTreeTableView();
        return treeTable != null &&
                treeTable.getSelectionModel() != null &&
                treeTable.getSelectionModel().isCellSelectionEnabled();
    }
    
    /*
     * This was brought in to fix the issue in RT-22077, namely that the 
     * ObservableValue was being GC'd, meaning that changes to the value were
     * no longer being delivered. By extracting this value out of the method, 
     * it is now referred to from TableCell and will therefore no longer be
     * GC'd.
     */
    private ObservableValue<T> currentObservableValue = null;

    private boolean isFirstRun = true;

    /*
     * This is called when we think that the data within this TreeTableCell may have
     * changed. You'll note that this is a private function - it is only called
     * when one of the triggers above call it.
     */
    private void updateItem() {
        if (currentObservableValue != null) {
            currentObservableValue.removeListener(weaktableRowUpdateObserver);
        }
        
        // get the total number of items in the data model
        final TreeTableView<S> tableView = getTreeTableView();
        final TreeTableColumn<S,T> tableColumn = getTableColumn();
        final int itemCount = tableView == null ? -1 : getTreeTableView().getExpandedItemCount();
        final int index = getIndex();
        final boolean isEmpty = isEmpty();
        final T oldValue = getItem();

        final boolean indexExceedsItemCount = index >= itemCount;
        
        // there is a whole heap of reasons why we should just punt...
        if (indexExceedsItemCount ||
                index < 0 || 
                columnIndex < 0 ||
                !isVisible() ||
                tableColumn == null || 
                !tableColumn.isVisible() ||
                tableView.getRoot() == null) {

            // RT-30484 We need to allow a first run to be special-cased to allow
            // for the updateItem method to be called at least once to allow for
            // the correct visual state to be set up. In particular, in RT-30484
            // refer to Ensemble8PopUpTree.png - in this case the arrows are being
            // shown as the new cells are instantiated with the arrows in the
            // children list, and are only hidden in updateItem.
            // RT-32621: There are circumstances where we need to updateItem,
            // even when the index is greater than the itemCount. For example,
            // RT-32621 identifies issues where a TreeTableView collapses a
            // TreeItem but the custom cells remain visible. This is now
            // resolved with the check for indexExceedsItemCount.
            if ((!isEmpty && oldValue != null) || isFirstRun || indexExceedsItemCount) {
                updateItem(null, true);
                isFirstRun = false;
            }
            return;
        } else {
            currentObservableValue = tableColumn.getCellObservableValue(index);
            
            final T newValue = currentObservableValue == null ? null : currentObservableValue.getValue();
            
            if ((newValue != null && ! newValue.equals(oldValue)) ||
                    oldValue != null && ! oldValue.equals(newValue)) {
                updateItem(newValue, false);
            } else if(isEmpty && newValue == null) {
                updateItem(newValue, false);
            }
        }
        
        if (currentObservableValue == null) {
            return;
        }
        
        // add property change listeners to this item
        currentObservableValue.addListener(weaktableRowUpdateObserver);
    }

    @Override protected void layoutChildren() {
        if (itemDirty) {
            updateItem();
            itemDirty = false;
        }
        super.layoutChildren();
    }

    


    /***************************************************************************
     *                                                                         *
     *                              Expert API                                 *
     *                                                                         *
     **************************************************************************/

    /**
     * Updates the TreeTableView associated with this TreeTableCell. This is typically
     * only done once when the TreeTableCell is first added to the TreeTableView.
     *
     * @expert This function is intended to be used by experts, primarily
     *         by those implementing new Skins. It is not common
     *         for developers or designers to access this function directly.
     */
    public final void updateTreeTableView(TreeTableView<S> tv) {
        setTreeTableView(tv);
    }

    /**
     * Updates the TreeTableRow associated with this TreeTableCell.
     *
     * @expert This function is intended to be used by experts, primarily
     *         by those implementing new Skins. It is not common
     *         for developers or designers to access this function directly.
     */
    public final void updateTreeTableRow(TreeTableRow<S> treeTableRow) {
        this.setTreeTableRow(treeTableRow);
    }

    /**
     * Updates the TreeTableColumn associated with this TreeTableCell.
     *
     * @expert This function is intended to be used by experts, primarily
     *         by those implementing new Skins. It is not common
     *         for developers or designers to access this function directly.
     */
    public final void updateTreeTableColumn(TreeTableColumn<S,T> col) {
        // remove style class of existing tree table column, if it is non-null
        TreeTableColumn<S,T> oldCol = getTableColumn();
        if (oldCol != null) {
            oldCol.getStyleClass().removeListener(weakColumnStyleClassListener);
            getStyleClass().removeAll(oldCol.getStyleClass());
        }
        
        setTableColumn(col);
        
        if (col != null) {
            getStyleClass().addAll(col.getStyleClass());
            col.getStyleClass().addListener(weakColumnStyleClassListener);
        }
    }



    /***************************************************************************
     *                                                                         *
     * Stylesheet Handling                                                     *
     *                                                                         *
     **************************************************************************/

    private static final String DEFAULT_STYLE_CLASS = "tree-table-cell";
    private static final PseudoClass PSEUDO_CLASS_LAST_VISIBLE = 
            PseudoClass.getPseudoClass("last-visible");

    /** {@inheritDoc} */
    @Override protected Skin<?> createDefaultSkin() {
        return new TreeTableCellSkin<S,T>(this);
    }
}
