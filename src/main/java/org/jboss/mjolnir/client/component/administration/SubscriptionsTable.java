package org.jboss.mjolnir.client.component.administration;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gwt.cell.client.ActionCell;
import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.CheckboxCell;
import com.google.gwt.cell.client.CompositeCell;
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.HasCell;
import com.google.gwt.cell.client.TextInputCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.cellview.client.SimplePager;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.view.client.ListDataProvider;
import org.jboss.mjolnir.authentication.KerberosUser;
import org.jboss.mjolnir.client.ExceptionHandler;
import org.jboss.mjolnir.client.component.table.ConditionalActionCell;
import org.jboss.mjolnir.client.component.table.DropDownCell;
import org.jboss.mjolnir.client.component.table.TwoRowHeaderBuilder;
import org.jboss.mjolnir.client.domain.Subscription;
import org.jboss.mjolnir.client.service.AdministrationService;
import org.jboss.mjolnir.client.service.AdministrationServiceAsync;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Table composite displaying list of Subscriptions objects.
 * <p/>
 * Contains action button for editing and deleting related users. Allows filtering and sorting.
 *
 * @author Tomas Hofman (thofman@redhat.com)
 */
public class SubscriptionsTable extends Composite {

    private static final int PAGE_SIZE = 50;
    private static final List<String> KRB_ACCOUNT_FILTER_OPTIONS = new ArrayList<String>();

    static {
        KRB_ACCOUNT_FILTER_OPTIONS.add("-");
        KRB_ACCOUNT_FILTER_OPTIONS.add("yes");
        KRB_ACCOUNT_FILTER_OPTIONS.add("no");
    }

    private static final Logger logger = Logger.getLogger(SubscriptionsTable.class.getName());

    protected HTMLPanel panel = new HTMLPanel("");
    private HTMLPanel buttonsPanel = new HTMLPanel("");
    private HTMLPanel dataPanel = new HTMLPanel("");
    private AdministrationServiceAsync administrationService = AdministrationService.Util.getInstance();
    protected ListDataProvider<Subscription> dataProvider;
    protected SubscriptionSearchPredicate searchPredicate;
    private List<Button> actionButtons = new ArrayList<Button>();
    private Set<Subscription> selectedItems = new HashSet<Subscription>();
    private List<Subscription> subscriptionList;

    public SubscriptionsTable(List<Subscription> subscriptions) {
        initWidget(panel);
        initStyles();

        initActionPanel();

        final CellTable<Subscription> subscriptionTable = new CellTable<Subscription>();
        subscriptionTable.setKeyboardSelectionPolicy(HasKeyboardSelectionPolicy.KeyboardSelectionPolicy.DISABLED);
        dataPanel.add(subscriptionTable);
        panel.add(dataPanel);

        subscriptionList = subscriptions;
        searchPredicate = new SubscriptionSearchPredicate();
//        dataProvider = new FilteringListDataProvider<Subscription>(subscriptions, searchPredicate);
        dataProvider = new ListDataProvider<Subscription>(subscriptionList);
        dataProvider.addDataDisplay(subscriptionTable);


        // column definitions

        final CheckboxCell selectionCell = new CheckboxCell(false, true);
        final Column<Subscription, Boolean> selectionCol = new Column<Subscription, Boolean>(selectionCell) {
            @Override
            public Boolean getValue(Subscription object) {
                return selectedItems.contains(object);
            }
        };
        subscriptionTable.addColumn(selectionCol);
        selectionCol.setFieldUpdater(new FieldUpdater<Subscription, Boolean>() {
            @Override
            public void update(int index, Subscription object, Boolean value) {
                if (value) {
                    selectedItems.add(object);
                } else {
                    selectedItems.remove(object);
                }

                enableActionButtons(selectedItems.size() > 0);
            }
        });

        final TextColumn<Subscription> krbNameCol = new TextColumn<Subscription>() {
            @Override
            public String getValue(Subscription object) {
                return object.getKerberosName();
            }
        };
        krbNameCol.setSortable(true);
        subscriptionTable.addColumn(krbNameCol, "Kerberos Username");

        final TextColumn<Subscription> gitHubNameCol = new TextColumn<Subscription>() {
            @Override
            public String getValue(Subscription object) {
                return object.getGitHubName();
            }
        };
        gitHubNameCol.setSortable(true);
        subscriptionTable.addColumn(gitHubNameCol, "GitHub Username");

        final TextColumn<Subscription> krbAccCol = new TextColumn<Subscription>() {
            @Override
            public String getValue(Subscription object) {
                return object.isActiveKerberosAccount() ? "yes" : "no";
            }
        };
        krbAccCol.setSortable(true);
        subscriptionTable.addColumn(krbAccCol, "Krb Account?");

        final TextColumn<Subscription> whitelistCol = new TextColumn<Subscription>() {
            @Override
            public String getValue(Subscription object) {
                return object.isWhitelisted() ? "yes" : "no";
            }
        };
        whitelistCol.setSortable(true);
        subscriptionTable.addColumn(whitelistCol, "Whitelist?");

        subscriptionTable.addColumn(createActionColumn(), "Actions");


        // sorting

        final ColumnSortEvent.ListHandler<Subscription> sortHandler =
                new ColumnSortEvent.ListHandler<Subscription>(subscriptions) {
                    @Override
                    public void onColumnSort(ColumnSortEvent event) {
                        super.onColumnSort(event);
                        dataProvider.refresh();
                    }
                };
        sortHandler.setComparator(krbNameCol, new KrbNameComparator());
        sortHandler.setComparator(gitHubNameCol, new GitHubNameComparator());
        sortHandler.setComparator(krbAccCol, new IsRegisteredComparator());
        subscriptionTable.addColumnSortHandler(sortHandler);
//        subscriptionTable.getColumnSortList().push(krbNameCol);


        // paging

        final SimplePager pager = new SimplePager();
        pager.setDisplay(subscriptionTable);
        pager.setPageSize(PAGE_SIZE);
        dataPanel.add(pager);


        // filtering

        final List<Header<?>> filterHeaders = createFilterHeaders();
        subscriptionTable.setHeaderBuilder(new TwoRowHeaderBuilder(subscriptionTable, false, filterHeaders));
    }

    protected void initStyles() {
        Style style = panel.getElement().getStyle();
        style.setHeight(100, Style.Unit.PCT);
        style.setPosition(Style.Position.RELATIVE);

        Style dataStyle = dataPanel.getElement().getStyle();
        dataStyle.setPosition(Style.Position.ABSOLUTE);
        dataStyle.setTop(40, Style.Unit.PX);
        dataStyle.setBottom(60, Style.Unit.PX);
        dataStyle.setLeft(1, Style.Unit.EM);
        dataStyle.setRight(1, Style.Unit.EM);
        dataStyle.setOverflowY(Style.Overflow.AUTO);
    }

    public void addAction(String caption, final ActionDelegate delegate) {
        Button button = new Button(caption, new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                List<Subscription> selectedItemsCopy = new ArrayList<Subscription>(selectedItems);
                delegate.execute(selectedItemsCopy);
            }
        });
        button.setEnabled(false);
        actionButtons.add(button);
        buttonsPanel.add(button);
        buttonsPanel.add(new HTMLPanel("span", " "));
    }

    private void enableActionButtons(boolean enable) {
        for (Button button: actionButtons) {
            button.setEnabled(enable);
        }
    }

    private void initActionPanel() {
        panel.add(buttonsPanel);

        Style style = buttonsPanel.getElement().getStyle();
        style.setProperty("borderBottom", "1px solid #999");
        style.setProperty("paddingBottom", "7px");

        addDefaultActions();
    }

    protected void addDefaultActions() {
        addAction("Whitelist", new WhitelistDelegate(true));
        addAction("Un-whitelist", new WhitelistDelegate(false));
    }

    public Set<Subscription> getSelectedItems() {
        return selectedItems;
    }

    public List<Subscription> getItemList() {
        return subscriptionList;
    }

    public ListDataProvider<Subscription> getDataProvider() {
        return dataProvider;
    }

    public void refresh() {
        List<Subscription> filteredList = Lists.newArrayList(Iterables.filter(subscriptionList, searchPredicate));
        dataProvider.setList(filteredList);
//        dataProvider.refresh();
    }

    /**
     * Creates list of headers containing input boxes for specifying filtering criteria.
     *
     * @return list of headers
     */
    protected List<Header<?>> createFilterHeaders() {
        final List<Header<?>> filterHeaders = new ArrayList<Header<?>>();
        filterHeaders.add(null);

        // krb name
        Cell<String> krbNameInputCell = new TextInputCell();
        Header<String> krbNameFilterHeader = new Header<String>(krbNameInputCell) {
            @Override
            public String getValue() {
                return "";
            }
        };
        krbNameFilterHeader.setUpdater(new ValueUpdater<String>() {
            @Override
            public void update(String value) {
                searchPredicate.setKrbNameExpression(value);
                refresh();
            }
        });
        filterHeaders.add(krbNameFilterHeader);

        // github name
        TextInputCell gitHubNameInputCell = new TextInputCell();
        Header<String> gitHubNameFilterHeader = new Header<String>(gitHubNameInputCell) {
            @Override
            public String getValue() {
                return "";
            }
        };
        gitHubNameFilterHeader.setUpdater(new ValueUpdater<String>() {
            @Override
            public void update(String value) {
                searchPredicate.setGitHubNameExpression(value);
                refresh();
            }
        });
        filterHeaders.add(gitHubNameFilterHeader);

        // krb account
        final DropDownCell krbAccountSelectionCell = new DropDownCell(KRB_ACCOUNT_FILTER_OPTIONS);
        Header<String> krbAccountFilterHeader = new Header<String>(krbAccountSelectionCell) {
            @Override
            public String getValue() {
                return krbAccountSelectionCell.getValue();
            }
        };
        krbAccountFilterHeader.setUpdater(new ValueUpdater<String>() {
            @Override
            public void update(String value) {
                Boolean boolValue;
                if ("yes".equals(value)) {
                    boolValue = true;
                } else if ("no".equals(value)) {
                    boolValue = false;
                } else {
                    boolValue = null;
                }

                searchPredicate.setKrbAccount(boolValue);
                refresh();
            }
        });
        filterHeaders.add(krbAccountFilterHeader);

        // whitelist
        final DropDownCell whitelistCell = new DropDownCell(KRB_ACCOUNT_FILTER_OPTIONS);
        Header<String> whitelistFilterHeader = new Header<String>(whitelistCell) {
            @Override
            public String getValue() {
                return whitelistCell.getValue();
            }
        };
        whitelistFilterHeader.setUpdater(new ValueUpdater<String>() {
            @Override
            public void update(String value) {
                Boolean boolValue;
                if ("yes".equals(value)) {
                    boolValue = true;
                } else if ("no".equals(value)) {
                    boolValue = false;
                } else {
                    boolValue = null;
                }

                searchPredicate.setWhitelisted(boolValue);
                refresh();
            }
        });
        filterHeaders.add(whitelistFilterHeader);

        return filterHeaders;
    }

    /**
     * Creates last table column with action buttons.
     *
     * @return action column
     */
    protected Column<Subscription, Subscription> createActionColumn() {
        final List<HasCell<Subscription, ?>> hasCells = createActionCells();

        final Cell<Subscription> cell = new CompositeCell<Subscription>(hasCells);
        return new Column<Subscription, Subscription>(cell) {
            @Override
            public Subscription getValue(Subscription object) {
                return object;
            }
        };
    }

    protected List<HasCell<Subscription, ?>> createActionCells() {
        final List<HasCell<Subscription, ?>> hasCells = new ArrayList<HasCell<Subscription, ?>>();

        // edit button
        hasCells.add(new ConditionalActionCell<Subscription>("Edit", new EditDelegate()));

        // subscriptions button
        hasCells.add(new ConditionalActionCell<Subscription>("Subscriptions", new SubscribeDelegate()) {
            @Override
            public boolean isEnabled(Subscription value) {
                return value.getGitHubName() != null;
            }
        });

        return hasCells;
    }

    /**
     * Called after item was modified.
     *
     * @param object    modified item
     * @param savedUser user instance that was actually saved on server
     */
    protected void onEdited(Subscription object, KerberosUser savedUser) {
        // updates subscription item in the list with current user object
        object.setKerberosUser(savedUser);
        object.setGitHubName(savedUser.getGithubName());
        dataProvider.refresh();
    }


    // comparators

    /**
     * Compares Subscription objects by krb name.
     */
    private class KrbNameComparator implements Comparator<Subscription> {
        @Override
        public int compare(Subscription subscription, Subscription subscription2) {
            if (subscription == null || subscription.getKerberosName() == null) {
                return -1;
            } else if (subscription2 == null || subscription2.getKerberosName() == null) {
                return 1;
            }
            return subscription.getKerberosName().toLowerCase()
                    .compareTo(subscription2.getKerberosName().toLowerCase());
        }
    }

    /**
     * Compares Subscription objects by GitHub name.
     */
    private class GitHubNameComparator implements Comparator<Subscription> {
        @Override
        public int compare(Subscription subscription, Subscription subscription2) {
            if (subscription == null || subscription.getGitHubName() == null) {
                return -1;
            } else if (subscription2 == null || subscription2.getGitHubName() == null) {
                return 1;
            }
            return subscription.getGitHubName().toLowerCase()
                    .compareTo(subscription2.getGitHubName().toLowerCase());
        }
    }

    /**
     * Compares Subscription objects according to whether they are related to a registered user.
     */
    private class IsRegisteredComparator implements Comparator<Subscription> {
        @Override
        public int compare(Subscription subscription, Subscription subscription2) {
            if (subscription == null) {
                return -1;
            } else if (subscription2 == null) {
                return 1;
            } else if (subscription.isActiveKerberosAccount() == subscription2.isActiveKerberosAccount()) {
                return 0;
            } else {
                return subscription.isActiveKerberosAccount() ? 1 : -1;
            }
        }
    }


    // button delegates

    /**
     * Edit button delegate.
     */
    private class EditDelegate implements ActionCell.Delegate<Subscription> {
        @Override
        public void execute(final Subscription object) {
            // displays edit dialog
            KerberosUser userToEdit = object.getKerberosUser();
            if (userToEdit == null) { // if user is not yet in our database, create new object
                userToEdit = new KerberosUser();
                userToEdit.setGithubName(object.getGitHubName());
            }
            final EditUserDialog editDialog = new EditUserDialog(userToEdit) {
                @Override
                protected void onSave(KerberosUser savedUser) {
                    onEdited(object, savedUser);
                }
            };
            editDialog.center();
        }
    }

    /**
     * Subscribe button delegate.
     */
    private class SubscribeDelegate implements ActionCell.Delegate<Subscription> {
        @Override
        public void execute(final Subscription object) {
            // displays subscription dialog
            final SubscribeUserDialog dialog = new SubscribeUserDialog(object.getGitHubName());
            dialog.center();
        }
    }


    // predicates

    public interface SearchPreditcate<T> extends Predicate<T> {
        boolean isEmpty();
    }

    /**
     * Predicate for filtering subscriptions according to given criteria.
     * <p/>
     * Any subscription with krb name and/or github name *containing* given strings qualifies.
     */
    private class SubscriptionSearchPredicate implements SearchPreditcate<Subscription> {

        private String krbNameExpression;
        private String gitHubNameExpression;
        private Boolean krbAccount;
        private Boolean whitelisted;

        @Override
        public boolean apply(@Nullable Subscription o) {
            if (o == null) {
                return false;
            } else if (isEmpty()) {
                return true;
            }
            return (isEmpty(krbNameExpression) || (o.getKerberosName() != null && o.getKerberosName().toLowerCase().contains(krbNameExpression.toLowerCase())))
                    && (isEmpty(gitHubNameExpression) || (o.getGitHubName() != null && o.getGitHubName().toLowerCase().contains(gitHubNameExpression.toLowerCase())))
                    && (krbAccount == null || krbAccount == o.isActiveKerberosAccount())
                    && (whitelisted == null || whitelisted == o.isWhitelisted());
        }

        public boolean isEmpty() {
            return isEmpty(krbNameExpression) && isEmpty(gitHubNameExpression) && krbAccount == null && whitelisted == null;
        }

        public void setKrbNameExpression(String krbNameExpression) {
            this.krbNameExpression = krbNameExpression;
        }

        public void setGitHubNameExpression(String gitHubNameExpression) {
            this.gitHubNameExpression = gitHubNameExpression;
        }

        public void setKrbAccount(Boolean krbAccount) {
            this.krbAccount = krbAccount;
        }

        public void setWhitelisted(Boolean whitelisted) {
            this.whitelisted = whitelisted;
        }

        private boolean isEmpty(String value) {
            return value == null || "".equals(value);
        }

        @Override
        public String toString() {
            return "SearchPredicate{" +
                    "krbNameExpression='" + krbNameExpression + '\'' +
                    ", gitHubNameExpression='" + gitHubNameExpression + '\'' +
                    ", krbAccount=" + krbAccount +
                    ", whitelisted=" + whitelisted +
                    '}';
        }
    }

    public static interface ActionDelegate {
        void execute(List<Subscription> selectedItems);
    }

    private class WhitelistDelegate implements ActionDelegate {

        private boolean whitelist;

        public WhitelistDelegate(boolean whitelist) {
            this.whitelist = whitelist;
        }

        @Override
        public void execute(final List<Subscription> selectedItems) {
            administrationService.whitelist(selectedItems, whitelist, new AsyncCallback<Collection<Subscription>>() {
                @Override
                public void onFailure(Throwable caught) {
                    ExceptionHandler.handle(caught);
                }

                @Override
                public void onSuccess(Collection<Subscription> result) {
                    for (Subscription subscription: result) {
                        int idx = selectedItems.indexOf(subscription);
                        if (idx > -1) {
                            Subscription originalSubscription = selectedItems.get(idx);
                            originalSubscription.setKerberosUser(subscription.getKerberosUser());
                        }
                    }
                    dataProvider.refresh();
                }
            });
        }
    }

}
