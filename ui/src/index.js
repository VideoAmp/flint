import React from 'react';
import ReactDOM from 'react-dom';
import reactStamp from 'react-stamp'

import injectTapEventPlugin from 'react-tap-event-plugin';
injectTapEventPlugin();

import app from './App';
import './index.css';

const App = reactStamp(React).compose(app);

ReactDOM.render(
  <App />,
  document.getElementById('root')
);
