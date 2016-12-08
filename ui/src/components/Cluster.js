import React from 'react';
import R from 'ramda';

import {Card, CardActions, CardHeader, CardText} from 'material-ui/Card';
import Trash from 'material-ui/svg-icons/action/delete';
import Add from 'material-ui/svg-icons/content/add';

import Instance from './Instance';

const exampleInstances = R.times((index) => <Instance key={index}/>);

export default () => (
    <Card>
        <CardHeader
            title="Kaleho-Test 2.1.0"
            titleStyle={{ "font-size": "125%"}}
        >
            <Trash/>
            <Add/>
        </CardHeader>
        <CardText>
            {exampleInstances(Math.floor(Math.random() * 5 + 1))}
        </CardText>
        <CardActions style={{ "backgroundColor": "#ccc"}}>
            <p>2 cores, 2GB RAM, $0.039/hr</p>
        </CardActions>
    </Card>
);
