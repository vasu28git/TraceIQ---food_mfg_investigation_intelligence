import { createBrowserRouter, Navigate } from 'react-router-dom'
import { PlatformAdminRoute } from '../components/PlatformAdminRoute'
import { GuestRoute, ProtectedRoute } from '../components/ProtectedRoute'
import { RootRedirect } from '../components/RootRedirect'
import { AppLayout, AuthLayout } from '../layouts/AuthLayout'
import { OrganisationLayout } from '../layouts/OrganisationLayout'
import { LoginPage } from '../pages/LoginPage'
import { ChangePasswordPage } from '../pages/ChangePasswordPage'
import { PlatformDashboard } from '../pages/platform/Dashboard'
import { OrganisationsPage } from '../pages/platform/OrganisationsPage'
import { CreateOrganisationPage } from '../pages/platform/CreateOrganisationPage'
import { OrganisationDetailsPage } from '../pages/platform/OrganisationDetailsPage'
import { OrganisationDashboard } from '../pages/organisation/Dashboard'
import { UsersPage } from '../pages/organisation/UsersPage'
import { RolesPage } from '../pages/organisation/RolesPage'
import { ConfigurationsPage } from '../pages/organisation/ConfigurationsPage'
import { IntegrationsPage } from '../pages/organisation/IntegrationsPage'
import { FilesPage } from '../pages/organisation/FilesPage'
import { InvestigationsPage } from '../pages/organisation/InvestigationsPage'
import { InvestigationWorkspacePage } from '../pages/organisation/InvestigationWorkspacePage'
import { ComplaintsPage } from '../pages/organisation/ComplaintsPage'
import { ComplaintDetailPage } from '../pages/organisation/ComplaintDetailPage'
import { EvidenceGraphPage } from '../pages/organisation/EvidenceGraphPage'
import { FindingDetailPage } from '../pages/organisation/FindingDetailPage'
import { Placeholder } from '../pages/organisation/Placeholder'
import { NotFoundPage } from '../pages/NotFoundPage'
import { ForbiddenPage } from '../pages/ForbiddenPage'

export const router = createBrowserRouter([
  // Public / guest – login redirects if already authenticated
  {
    element: <AuthLayout />,
    children: [
      {
        path: '/login',
        element: (
          <GuestRoute>
            <LoginPage />
          </GuestRoute>
        ),
      },
      // change-password must be reachable when authenticated & mustChangePassword=true
      // it is outside standard ProtectedRoute to avoid circular redirect loops
      { path: '/change-password', element: <ChangePasswordPage /> },
    ],
  },

  // Organisation workspace – organisation users only (not platform admin)
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <OrganisationLayout />,
        children: [
          { path: '/organisation/dashboard', element: <OrganisationDashboard /> },
          { path: '/organisation/complaints', element: <ComplaintsPage /> },
          { path: '/organisation/complaints/:id', element: <ComplaintDetailPage /> },
          { path: '/organisation/evidence-graph', element: <EvidenceGraphPage /> },
          { path: '/organisation/investigations', element: <InvestigationsPage /> },
          { path: '/organisation/investigations/:investigationId', element: <InvestigationWorkspacePage /> },
          { path: '/organisation/investigations/:investigationId/findings/:findingId', element: <FindingDetailPage /> },
          { path: '/organisation/incidents', element: <InvestigationsPage /> },
          { path: '/organisation/incidents/:investigationId', element: <InvestigationWorkspacePage /> },
          { path: '/organisation/users', element: <UsersPage /> },
          { path: '/organisation/roles', element: <RolesPage /> },
          { path: '/organisation/configurations', element: <ConfigurationsPage /> },
          { path: '/organisation/integrations', element: <IntegrationsPage /> },
          { path: '/organisation/files', element: <FilesPage /> },
          { path: '/organisation/settings', element: <Placeholder title="Organisation Settings" /> },
          { path: '/organisation/profile', element: <Placeholder title="Profile" /> },
          { path: '/forbidden', element: <ForbiddenPage /> },
        ],
      },
    ],
  },

  // Platform admin workspace – isolated from org workspace
  {
    element: <PlatformAdminRoute />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: '/platform/dashboard', element: <PlatformDashboard /> },
          { path: '/platform/organisations', element: <OrganisationsPage /> },
          { path: '/platform/organisations/new', element: <CreateOrganisationPage /> },
          { path: '/platform/organisations/:orgId', element: <OrganisationDetailsPage /> },
        ],
      },
    ],
  },

  // Smart root – decides based on auth state/role (src/store/authStore.ts: isPlatformAdmin)
  { path: '/', element: <RootRedirect /> },
  { path: '/404', element: <NotFoundPage /> },
  { path: '*', element: <NotFoundPage /> },
])
