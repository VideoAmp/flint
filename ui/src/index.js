import React from 'react';
import ReactDOM from 'react-dom';

import injectTapEventPlugin from 'react-tap-event-plugin';
injectTapEventPlugin();

import App from './App';
import './index.css';

ReactDOM.render(
  <App />,
  document.getElementById('root')
);
