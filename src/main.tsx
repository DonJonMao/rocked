import React from 'react';
import ReactDOM from 'react-dom/client';
import { InspectionProvider } from './app/InspectionContext';
import App from './app/App';
import './styles/app.css';

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><InspectionProvider><App /></InspectionProvider></React.StrictMode>);
