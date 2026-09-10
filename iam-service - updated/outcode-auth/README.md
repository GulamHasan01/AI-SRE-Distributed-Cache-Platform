# OutCode Auth — React Frontend

A structured, production-style React application for the OutCode developer identity platform.

## Project Structure

```
outcode-auth/
├── public/
│   └── index.html
├── src/
│   ├── api/
│   │   └── authApi.js          # All API calls in one place
│   ├── components/
│   │   ├── Alert.jsx            # Dismissible alert banner
│   │   ├── Alert.module.css
│   │   ├── Button.jsx           # Reusable button (primary / secondary / danger)
│   │   ├── Button.module.css
│   │   ├── FormField.jsx        # Labelled input field
│   │   ├── FormField.module.css
│   │   ├── Navbar.jsx           # Top navbar with OAuth buttons
│   │   └── Navbar.module.css
│   ├── hooks/
│   │   ├── useAlert.js          # Alert state management
│   │   └── useTokenStore.js     # localStorage token read/write/clear
│   ├── styles/
│   │   └── global.css           # CSS variables, reset, base styles
│   ├── views/
│   │   ├── AuthView.module.css  # Shared styles for all form views
│   │   ├── RegisterView.jsx
│   │   ├── ConfirmRegisterView.jsx
│   │   ├── LoginView.jsx
│   │   ├── TwoFaLoginView.jsx
│   │   ├── ForgotPasswordView.jsx
│   │   ├── DashboardView.jsx
│   │   ├── DashboardView.module.css
│   │   ├── TwoFaSetupView.jsx
│   │   ├── TwoFaConfirmView.jsx
│   │   ├── TwoFaDisableView.jsx
│   │   ├── ChangePasswordView.jsx
│   │   └── EmailVerifyView.jsx
│   ├── App.js                   # Main app + all route/handler logic
│   ├── App.module.css
│   └── index.js
├── .env
├── package.json
└── README.md
```

## Quick Start

### Prerequisites
- Node.js 16+
- Your backend running on `http://localhost:8080`

### Install & Run

```bash
cd outcode-auth
npm install
npm start
```

The app opens at **http://localhost:3000**.  
API calls to `/auth/*` and `/users/*` are proxied to `http://localhost:8080` via the `proxy` field in `package.json` — no CORS issues.

### Backend URL (optional)
If your backend runs on a different host/port, edit `.env`:
```
REACT_APP_BACKEND_URL=http://your-backend-host:port
```

## Auth Flows Covered

| Flow | Views |
|------|-------|
| Register | `RegisterView` → `ConfirmRegisterView` |
| Login | `LoginView` → (optional) `TwoFaLoginView` |
| Forgot password | `ForgotPasswordView` |
| Dashboard | `DashboardView` |
| 2FA Setup | `TwoFaSetupView` → `TwoFaConfirmView` |
| 2FA Disable | `TwoFaDisableView` |
| Change password | `ChangePasswordView` |
| Email verify | `EmailVerifyView` |
| OAuth | Google + LinkedIn links in `Navbar` |
