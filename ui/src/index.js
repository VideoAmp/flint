import React from "react";
import ReactDOM from "react-dom";
import injectTapEventPlugin from "react-tap-event-plugin";
import App from "./App";
import "./index.css";

injectTapEventPlugin();

fetch("endpoints.json")
  .then(response => response.json())
  .then((endpoints) => {
      const { serverUrl, messagingUrl } = endpoints;
      ReactDOM.render(
        <App serverUrl={serverUrl} messagingUrl={messagingUrl}/>,
        document.getElementById("root")
      );
  });
