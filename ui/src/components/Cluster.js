import React from 'react';
import R from 'ramda';

import NumberInput from 'material-ui-number-input';
import {Card, CardActions, CardHeader, CardText} from 'material-ui/Card';
import Dialog from 'material-ui/Dialog';
import FlatButton from 'material-ui/FlatButton';
import Trash from 'material-ui/svg-icons/action/delete';
import Add from 'material-ui/svg-icons/content/add';

import Instance from './Instance';

const exampleInstances = R.times((index) => <Instance key={index}/>);

export default class Cluster extends React.Component {
    state = {
        instanceDialogOpen: false,
    };

    handleInstanceDialogOpen = () => {
        this.setState({ instanceDialogOpen: true });
    };

    handleInstanceDialogClose = () => {
        this.setState({ instanceDialogOpen: false });
    };

    onInstanceCountError = (error) => {
        var errorText = (error === "none") ?
            "" :
            "Please enter a valid instance count (less than 100)";

        this.setState({ errorText });
    };

    render() {
        const instanceDialogActions = [
            <FlatButton
                label="Cancel"
                onTouchTap={this.handleInstanceDialogClose}
            />,
            <FlatButton
                label="Launch"
                primary={true}
                onTouchTap={this.handleInstanceDialogClose}
            />,
        ];

        return (
            <div>
                <Card>
                    <CardHeader
                        title="Kaleho-Test 2.1.0"
                        titleStyle={{ "font-size": "125%"}}
                    >
                        <Trash/>
                        <Add onTouchTap={this.handleInstanceDialogOpen}/>
                    </CardHeader>
                    <CardText>
                        {exampleInstances(Math.floor(Math.random() * 5 + 1))}
                    </CardText>
                    <CardActions style={{ "backgroundColor": "#ccc"}}>
                        <p>2 cores, 2GB RAM, $0.039/hr</p>
                    </CardActions>
                </Card>
                <Dialog
                    title="Add Worker"
                    actions={instanceDialogActions}
                    modal={false}
                    open={this.state.instanceDialogOpen}
                    onRequestClose={this.handleInstanceDialogClose}
                >
                    <NumberInput
                        id="instance-amount-input"
                        floatingLabelText="Instance Count"
                        defaultValue={1}
                        min={1}
                        max={100}
                        strategy="allow"
                        errorText={this.state.errorText}
                        onError={this.onInstanceCountError}
                    />
                </Dialog>
            </div>
        );
    }
}
