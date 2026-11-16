import React from 'react';
import { AppShell } from './components/AppShell';
import { PrintPreviewModal } from './components/PrintPreviewModal';
import { ToastContainer } from './components/ToastContainer';
import { WhatsAppShareModal } from './components/WhatsAppShareModal';
import { AppLockScreen } from './screens/AppLockScreen';
import { AuthScreen } from './screens/AuthScreen';
import { BackupJsonScreen } from './screens/BackupJsonScreen';
import { BusinessProfileScreen } from './screens/BusinessProfileScreen';
import { CashBankScreen } from './screens/CashBankScreen';
import { CmaProjectReportScreen } from './screens/CmaProjectReportScreen';
import { DashboardScreen } from './screens/DashboardScreen';
import { DaybookScreen } from './screens/DaybookScreen';
import { GstAssistantScreen } from './screens/GstAssistantScreen';
import { Gstr2bReconciliationScreen } from './screens/Gstr2bReconciliationScreen';
import { InventoryScreen } from './screens/InventoryScreen';
import { InvoiceMenuScreen } from './screens/InvoiceMenuScreen';
import { LedgerMenuScreen } from './screens/LedgerMenuScreen';
import { MoreMenuScreen } from './screens/MoreMenuScreen';
import { ReportsAndFinancialsScreen } from './screens/ReportsAndFinancialsScreen';
import { SecuritySettingsScreen } from './screens/SecuritySettingsScreen';
import { SuperAdminScreen } from './screens/SuperAdminScreen';
import { PabaruProvider, usePabaru } from './state/PabaruContext';

const PabaruRouter: React.FC = () => {
  const { state, activeScreen } = usePabaru();

  const renderScreen = () => {
    switch (activeScreen) {
      case 'dashboard':
        return <DashboardScreen />;
      case 'invoices':
        return <InvoiceMenuScreen />;
      case 'stock':
        return <InventoryScreen />;
      case 'cash_bank':
        return <CashBankScreen />;
      case 'daybook':
        return <DaybookScreen />;
      case 'ledgers':
      case 'party_unpaid':
        return <LedgerMenuScreen />;
      case 'reports':
        return <ReportsAndFinancialsScreen />;
      case 'cma_report':
        return <CmaProjectReportScreen />;
      case 'gst_assistant':
        return <GstAssistantScreen />;
      case 'gstr2b_recon':
        return <Gstr2bReconciliationScreen />;
      case 'business_profile':
        return <BusinessProfileScreen />;
      case 'backup':
        return <BackupJsonScreen />;
      case 'security':
        return <SecuritySettingsScreen />;
      case 'more':
        return <MoreMenuScreen />;
      default:
        return <DashboardScreen />;
    }
  };

  return (
    <>
      {state.isSuperAdminLoggedIn ? (
        <SuperAdminScreen />
      ) : !state.profile.isRegistered ? (
        <AuthScreen />
      ) : state.isAppLocked ? (
        <AppLockScreen />
      ) : (
        <AppShell>{renderScreen()}</AppShell>
      )}
      <ToastContainer />
      <PrintPreviewModal />
      <WhatsAppShareModal />
    </>
  );
};

export const App: React.FC = () => (
  <PabaruProvider>
    <PabaruRouter />
  </PabaruProvider>
);

export default App;
