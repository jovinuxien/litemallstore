import React from 'react';

interface IErrorBoundaryProps {
  readonly children: React.ReactNode;
  /** When this changes (e.g. route pathname), a caught error is cleared so
   *  navigating away recovers the app instead of sticking on the fallback. */
  readonly resetKey?: unknown;
}

interface IErrorBoundaryState {
  readonly error: any;
  readonly errorInfo: any;
}

class ErrorBoundary extends React.Component<IErrorBoundaryProps, IErrorBoundaryState> {
  readonly state: IErrorBoundaryState = { error: undefined, errorInfo: undefined };

  componentDidCatch(error, errorInfo) {
    this.setState({
      error,
      errorInfo,
    });
  }

  componentDidUpdate(prevProps: IErrorBoundaryProps) {
    if (this.state.errorInfo && prevProps.resetKey !== this.props.resetKey) {
      this.setState({ error: undefined, errorInfo: undefined });
    }
  }

  render() {
    const { error, errorInfo } = this.state;
    if (errorInfo) {
      const errorDetails = DEVELOPMENT ? (
        <details className='preserve-space'>
          {error && error.toString()}
          <br />
          {errorInfo.componentStack}
        </details>
      ) : undefined;
      return (
        <div>
          <h2 className='error'>An unexpected error has occurred.</h2>
          {errorDetails}
        </div>
      );
    }
    return this.props.children;
  }
}

export default ErrorBoundary;
