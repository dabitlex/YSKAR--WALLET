import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './stil.css';
import { App } from './App.tsx';
import { fehlerspeicherInstallieren } from './geraet/plattform.ts';
import { linksVerbinden } from './geraet/geraet.ts';

fehlerspeicherInstallieren();
linksVerbinden();
createRoot(document.getElementById('wurzel')!).render(<StrictMode><App /></StrictMode>);
