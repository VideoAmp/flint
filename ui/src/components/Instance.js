import React from 'react';
import {ListItem} from 'material-ui/List';
import Avatar from 'material-ui/Avatar';
import Trash from 'material-ui/svg-icons/action/delete';

import { green300, yellow300 } from 'material-ui/styles/colors';

import 'react-flexr/styles.css'

const stateColorMap = {
    "Starting": yellow300,
    "Running": green300
};
const getStatusIndicatorColor = (state) => stateColorMap[state];

const Instance = ({data, master}) => (
    <ListItem
        primaryText={`${data.ipAddress} ${master ? "Master" : "Worker"}`}
        leftAvatar={ <Avatar backgroundColor={getStatusIndicatorColor(data.state)} size={15} style={{margin: 12.5}} />}
        rightIcon={<Trash />}
    />
);

export default Instance;
